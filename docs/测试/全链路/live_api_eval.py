# -*- coding: utf-8 -*-
"""用前端同一套 HTTP 入口回放 20 万用例：登录 → 发消息/改卡/确认/下单，结果真实落库。"""
from __future__ import annotations

import argparse
import csv
import json
import queue
import sys
import threading
import time
import uuid
from collections import Counter, defaultdict
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from typing import Any, Optional
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

HERE = Path(__file__).resolve().parent
CSV_PATH = HERE / "triage-cases.csv"
OUT_JSONL = HERE / "_live_api_eval.jsonl"
OUT_SUMMARY = HERE / "_live_api_eval_summary.json"

BASE = "http://127.0.0.1:8080"
CAPTCHA = "000000"
TIMEOUT_SEC = 115
STATUS_NAME = {
    1: "待描述",
    2: "待补槽",
    3: "急症分流",
    4: "卡片待确认",
    5: "已确认",
    6: "需要人工",
    7: "安全兜底",
    8: "渠道不适用",
}
ROUTE_CERTAIN = {
    "SINGLE_SPECIALTY", "CANON_TERM", "PEDIATRIC", "OVERRIDE",
    "UNCERTAIN_MARKER", "SAME_L1_EARLIEST", "NO_HIT_SHORT",
}
ROUTE_FIXED = {"UNCERTAIN_MARKER", "OVERRIDE"}
CANON_UNCERTAIN = {"NO_HIT", "AMBIGUOUS_ONLY"}
print_lock = threading.Lock()
file_lock = threading.Lock()


def should_call_model(row: dict) -> bool:
    rule = (row.get("expect_route_reason_rule") or "").strip()
    canon = (row.get("expect_canon_reason") or "").strip()
    if canon.startswith("MODEL_"):
        return True
    route_certain = rule in ROUTE_CERTAIN
    route_fixed = rule in ROUTE_FIXED
    canon_certain = canon not in CANON_UNCERTAIN
    return (not route_certain) or ((not canon_certain) and (not route_fixed))


class ApiClient:
    def __init__(self, mobile: str):
        self.mobile = mobile
        self.token = ""

    def login(self) -> None:
        body = self._json("POST", "/api/auth/login", {"mobile": self.mobile, "captcha": CAPTCHA}, auth=False)
        token = ((body.get("data") or {}).get("token") or "")
        if body.get("code") != 200 or not token:
            raise RuntimeError(f"login failed {self.mobile}: {body.get('code')} {body.get('message')}")
        self.token = token

    def send(self, consultation_id: Optional[str], text: str) -> dict:
        return self._json("POST", "/api/consultations/messages", {
            "consultationId": consultation_id,
            "messageId": str(uuid.uuid4()),
            "text": text,
        })

    def patch(self, consultation_id: str, **fields: Any) -> dict:
        payload = {"consultationId": consultation_id}
        payload.update({key: value for key, value in fields.items() if value is not None})
        return self._json("PATCH", "/api/consultations/card", payload)

    def confirm(self, consultation_id: str) -> dict:
        return self._json("POST", "/api/consultations/confirmation", {"consultationId": consultation_id})

    def order(self, consultation_id: str, package_code: int = 1) -> dict:
        return self._json("POST", "/api/consultations/orders", {
            "consultationId": consultation_id,
            "packageCode": package_code,
            "consultMinutes": 15,
        })

    def wait_assignment(self, consultation_id: str) -> dict:
        return self._json("GET", f"/api/consultations/{consultation_id}/assignment?waitMs=8000", None)

    def end(self, consultation_id: str) -> dict:
        return self._json("POST", "/api/consultations/end", {"consultationId": consultation_id, "reason": "ENDED"})

    def get(self, consultation_id: str) -> dict:
        return self._json("GET", f"/api/consultations/{consultation_id}", None)

    def _json(self, method: str, path: str, payload: Optional[dict], auth: bool = True) -> dict:
        data = None if payload is None else json.dumps(payload, ensure_ascii=False).encode("utf-8")
        headers = {"Content-Type": "application/json"}
        if auth:
            headers["Authorization"] = "Bearer " + self.token
        last_error = None
        for attempt in range(4):
            req = Request(BASE + path, data=data, headers=headers, method=method)
            try:
                with urlopen(req, timeout=TIMEOUT_SEC) as response:
                    return json.loads(response.read().decode("utf-8"))
            except HTTPError as error:
                raw = error.read().decode("utf-8", errors="replace")
                if error.code in (429, 503) and attempt < 3:
                    time.sleep(0.4 * (attempt + 1))
                    last_error = error
                    continue
                try:
                    return json.loads(raw)
                except json.JSONDecodeError:
                    return {"code": error.code, "message": raw[:300], "http": error.code}
            except (URLError, TimeoutError) as error:
                last_error = error
                if attempt < 3:
                    time.sleep(0.4 * (attempt + 1))
                    continue
                return {"code": 0, "message": str(error), "http": "TIMEOUT"}
        return {"code": 0, "message": str(last_error), "http": "RETRY_EXHAUSTED"}


def view_of(body: dict) -> dict:
    data = body.get("data")
    if not isinstance(data, dict):
        return {}
    if "view" in data and isinstance(data.get("view"), dict):
        return data["view"]
    return data


def card_of(view: dict) -> dict:
    card = view.get("card")
    return card if isinstance(card, dict) else {}


def play(row: dict, client: ApiClient) -> dict:
    action = (row.get("action") or "MESSAGE").strip() or "MESSAGE"
    text = (row.get("input") or "").strip()
    prior = (row.get("prior_input") or "").strip()
    started = time.time()
    opened = None
    cid = None
    last = None

    def send_open(content: str) -> dict:
        nonlocal opened, cid, last
        opened = client.send(None, content)
        last = opened
        cid = view_of(opened).get("consultationId")
        return opened

    try:
        if action == "MESSAGE":
            if prior and prior != text:
                send_open(prior)
                if cid and last.get("code") == 200:
                    last = client.send(cid, text)
            else:
                send_open(text)
        else:
            send_open(prior or text)
            if last.get("code") == 200 and cid:
                status = view_of(last).get("status")
                if action == "PATCH":
                    last = client.patch(cid, duration="两天")
                elif action == "CONFIRM":
                    if status == 4:
                        last = client.confirm(cid)
                elif action == "ORDER":
                    if status == 4:
                        last = client.confirm(cid)
                    if last.get("code") == 200 and view_of(last).get("status") == 5:
                        last = client.order(cid)
                elif action == "DISPATCH":
                    if status == 4:
                        last = client.confirm(cid)
                    if last.get("code") == 200 and view_of(last).get("status") == 5:
                        last = client.order(cid)
                    if last.get("code") == 200 and view_of(last).get("order"):
                        last = client.wait_assignment(cid)
                elif action == "CANCEL":
                    if status == 4:
                        confirmed = client.confirm(cid)
                        if confirmed.get("code") == 200:
                            last = confirmed
                    if last.get("code") == 200:
                        last = client.end(cid)
                elif action == "MESSAGE_AFTER_CARD":
                    last = client.send(cid, text or "还想再问一句")
                elif action == "MESSAGE_AFTER_CONFIRM":
                    if status == 4:
                        last = client.confirm(cid)
                    if last.get("code") == 200:
                        last = client.send(cid, text or "确认后再说一句")
                elif action == "MESSAGE_AFTER_ENDED":
                    if status == 4:
                        last = client.confirm(cid)
                    if last.get("code") == 200 and view_of(last).get("status") == 5:
                        last = client.order(cid)
                    if last.get("code") == 200:
                        last = client.end(cid)
                    if last.get("code") == 200 and cid:
                        last = client.send(cid, text or "结束后再发")
    except Exception as error:
        return {
            "case_id": row.get("case_id"),
            "ok": False,
            "fail_bucket": "客户端异常",
            "error": str(error),
            "action": action,
            "latency_ms": int((time.time() - started) * 1000),
        }

    view = view_of(last or {})
    cid = view.get("consultationId") or cid
    persisted = False
    persisted_view = {}
    if cid and (last or {}).get("code") == 200:
        stored = client.get(cid)
        persisted_view = view_of(stored)
        persisted = stored.get("code") == 200 and persisted_view.get("consultationId") == cid
        if persisted_view:
            view = persisted_view
    card = card_of(view)
    return score_row(row, last or {}, view, card, cid, persisted, int((time.time() - started) * 1000))


def score_row(row: dict, body: dict, view: dict, card: dict, cid: Optional[str], persisted: bool, latency_ms: int) -> dict:
    expect_status = to_int(row.get("expect_status_code"))
    expect_error = to_int(row.get("expect_error_code"))
    gold = (row.get("gold_specialty") or "").strip() or (row.get("expect_specialty") or "").strip()
    acceptable = [item.strip() for item in (row.get("acceptable_specialties") or "").split(";") if item.strip()]
    status = to_int(view.get("status"))
    specialty = (card.get("specialty") or "").strip()
    source = (card.get("departmentSource") or "").strip()
    code = to_int(body.get("code"))
    need_model = should_call_model(row)
    did_model = source == "MODEL"
    http_flag = body.get("http")

    action = (row.get("action") or "MESSAGE").strip() or "MESSAGE"
    order = view.get("order") if isinstance(view.get("order"), dict) else None
    fail = None
    if http_flag == "TIMEOUT" or code == 0:
        fail = "接口超时"
    elif http_flag in (429, "429"):
        fail = "网关限流"
    elif expect_error:
        if code != expect_error:
            fail = f"期望业务错误{expect_error} 实际{code}"
    elif code != 200:
        fail = f"接口失败 code={code} {body.get('message') or ''}".strip()
    elif not persisted:
        fail = "接口成功但回查未落库"
    elif action == "CONFIRM" and status != 5:
        fail = f"确认后状态不是已确认 实际{status}"
    elif action == "ORDER" and not order:
        fail = "下单后没有订单"
    elif action == "DISPATCH" and not order:
        fail = "派医生前没有订单"
    elif action in ("MESSAGE", "MESSAGE_AFTER_CARD", "MESSAGE_AFTER_CONFIRM", "MESSAGE_AFTER_ENDED"):
        if expect_status and status != expect_status:
            fail = f"状态不符 期望{expect_status} 实际{status}"
        elif gold and specialty and gold != specialty and gold not in acceptable and specialty not in acceptable:
            fail = f"挂科错误 期望{gold} 实际{specialty}"
        elif need_model and source == "RULE":
            fail = "该走模型却走了规则"
        elif (not need_model) and source == "MODEL":
            fail = "不该走模型却走了模型"
        elif source == "FALLBACK":
            fail = "模型/规则兜底到FALLBACK"

    return {
        "case_id": row.get("case_id"),
        "action": row.get("action") or "MESSAGE",
        "suite": row.get("suite") or "",
        "primary_lane": row.get("primary_lane") or "",
        "expression_family": row.get("expression_family") or "",
        "clinical_category": row.get("clinical_category") or "",
        "consultation_id": cid,
        "code": code,
        "status": status,
        "status_name": STATUS_NAME.get(status or 0, ""),
        "specialty": specialty,
        "gold_specialty": gold,
        "source": source,
        "need_model": need_model,
        "did_model": did_model,
        "persisted": persisted,
        "latency_ms": latency_ms,
        "ok": fail is None,
        "fail_bucket": fail or "",
        "prompt": (view.get("prompt") or "")[:80],
        "http": http_flag or "",
    }


def to_int(value: Any) -> Optional[int]:
    if value in (None, ""):
        return None
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def load_done() -> set[str]:
    done: set[str] = set()
    if not OUT_JSONL.exists():
        return done
    with OUT_JSONL.open("r", encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line:
                continue
            try:
                item = json.loads(line)
            except json.JSONDecodeError:
                continue
            if item.get("case_id"):
                done.add(item["case_id"])
    return done


def append_result(item: dict) -> None:
    with file_lock:
        with OUT_JSONL.open("a", encoding="utf-8") as handle:
            handle.write(json.dumps(item, ensure_ascii=False) + "\n")


def summarize(rows: list[dict]) -> dict:
    total = len(rows)
    ok = sum(1 for item in rows if item.get("ok"))
    persisted = sum(1 for item in rows if item.get("persisted"))
    by_lane = defaultdict(lambda: Counter())
    buckets = Counter()
    model_gate = Counter()
    for item in rows:
        lane = item.get("primary_lane") or "unknown"
        by_lane[lane]["n"] += 1
        if item.get("ok"):
            by_lane[lane]["ok"] += 1
        if item.get("fail_bucket"):
            buckets[item["fail_bucket"].split(" ")[0]] += 1
            buckets[item["fail_bucket"]] += 1
        if item.get("need_model") and item.get("did_model"):
            model_gate["该走且走了"] += 1
        elif item.get("need_model") and not item.get("did_model"):
            model_gate["该走却没走"] += 1
        elif (not item.get("need_model")) and item.get("did_model"):
            model_gate["不该走却走了"] += 1
        else:
            model_gate["不该走且没走"] += 1
    lane_acc = {
        name: {
            "n": counts["n"],
            "ok": counts["ok"],
            "accuracy": round(100.0 * counts["ok"] / counts["n"], 2) if counts["n"] else None,
        }
        for name, counts in sorted(by_lane.items())
    }
    return {
        "total": total,
        "ok": ok,
        "accuracy": round(100.0 * ok / total, 2) if total else None,
        "persisted": persisted,
        "persist_rate": round(100.0 * persisted / total, 2) if total else None,
        "by_lane": lane_acc,
        "fail_buckets": dict(buckets.most_common(30)),
        "model_gate": dict(model_gate),
    }


def log(message: str) -> None:
    with print_lock:
        print(message, flush=True)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--limit", type=int, default=0, help="只跑前 N 条，0 表示全部")
    parser.add_argument("--workers", type=int, default=8)
    parser.add_argument("--offset", type=int, default=0)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if not CSV_PATH.exists():
        print("找不到", CSV_PATH, file=sys.stderr)
        return 1
    done = load_done()
    rows: list[dict] = []
    with CSV_PATH.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        for index, row in enumerate(reader):
            if index < args.offset:
                continue
            if args.limit and len(rows) >= args.limit:
                break
            case_id = row.get("case_id") or ""
            if case_id in done:
                continue
            rows.append(row)
    log(f"待跑 {len(rows)} 条，已完成 {len(done)}，workers={args.workers}")
    if not rows:
        return 0

    clients: queue.Queue[ApiClient] = queue.Queue()
    for worker in range(max(args.workers, 1)):
        client = ApiClient(f"138{worker:08d}")
        client.login()
        clients.put(client)
        log(f"账号就绪 {client.mobile}")

    scored: list[dict] = []
    scored_lock = threading.Lock()
    started = time.time()

    def run_one(item: tuple[int, dict]) -> dict:
        _index, row = item
        client = clients.get()
        try:
            result = play(row, client)
        except Exception as error:
            result = {
                "case_id": row.get("case_id"),
                "ok": False,
                "fail_bucket": "客户端异常",
                "error": str(error),
                "action": row.get("action"),
            }
        finally:
            clients.put(client)
        append_result(result)
        with scored_lock:
            scored.append(result)
            finished = len(scored)
            if finished == 1 or finished % 50 == 0 or finished == len(rows):
                summary = summarize(scored)
                OUT_SUMMARY.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
                elapsed = max(time.time() - started, 0.001)
                log(
                    f"进度 {finished}/{len(rows)} 准确率 {summary['accuracy']} "
                    f"落库 {summary['persist_rate']} 速度 {finished / elapsed:.1f}/s"
                )
        return result

    with ThreadPoolExecutor(max_workers=max(args.workers, 1)) as pool:
        futures = [pool.submit(run_one, item) for item in enumerate(rows)]
        for future in as_completed(futures):
            future.result()

    summary = summarize(scored)
    OUT_SUMMARY.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    log(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0 if summary["ok"] == summary["total"] else 2


if __name__ == "__main__":
    raise SystemExit(main())
