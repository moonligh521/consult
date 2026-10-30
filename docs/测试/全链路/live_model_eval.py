# -*- coding: utf-8 -*-
"""对 15 万全链路集做分层实跑：规则 CERTAIN 不调模型；规则定不了应走到模型。

默认打本机网关。15 万条全打真实模型会打爆配额与耗时，本脚本：
1. 离线扫完全量 CSV，给出规则/模型分流底数
2. 分层抽样打线上消息接口，核对 departmentSource 与金标二级科
3. 抽几条走登录→出卡→确认→支付→派医生→结束

用法：
    python docs/测试/全链路/live_model_eval.py
"""
from __future__ import annotations

import csv
import json
import os
import random
import socket
import sys
import time
import urllib.error
import urllib.request
import uuid
from collections import Counter, defaultdict
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
CSV_PATH = HERE / "triage-cases.csv"
OUT_JSON = HERE / "live-eval-report.json"
BASE = os.environ.get("CONSULT_GATEWAY", "http://127.0.0.1:8080").rstrip("/")
SMS_CODE = os.environ.get("AUTH_SMS_DEV_CODE", "000000")
TIMEOUT = 25
WORKERS = int(os.environ.get("LIVE_EVAL_WORKERS", "4"))
SEED = 27

CARD_READY = 4
CONFIRMED = 5
URGENT = 3
NEED_HUMAN = 6
UNSUITABLE = 8
PAY_PAID = 2

BUCKET_CAP = {
    "rule_ok": 50,
    "rule_cant": 80,
    "oral_certain_wrong": 40,
    "oral_uncertain": 40,
    "safety_urgent": 20,
}


class Fail(Exception):
    pass


def wait_port(host: str, port: int, seconds: float = 90) -> None:
    deadline = time.time() + seconds
    last = None
    while time.time() < deadline:
        try:
            with socket.create_connection((host, port), timeout=2):
                return
        except OSError as error:
            last = error
            time.sleep(0.4)
    raise Fail(f"{host}:{port} 未就绪: {last}")


def call(method: str, path: str, token: str | None = None, body: dict | None = None) -> dict:
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    headers = {"Accept": "application/json"}
    if data is not None:
        headers["Content-Type"] = "application/json; charset=utf-8"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
            raw = response.read().decode("utf-8")
    except urllib.error.HTTPError as error:
        raw = error.read().decode("utf-8", errors="replace")
        raise Fail(f"{method} {path} HTTP {error.code}: {raw[:400]}") from error
    except urllib.error.URLError as error:
        raise Fail(f"{method} {path} 连不上网关: {error.reason}") from error
    payload = json.loads(raw)
    if not isinstance(payload, dict):
        raise Fail(f"{method} {path} 返回不是对象")
    return payload


def ok(payload: dict, path: str) -> dict:
    if payload.get("code") != 200:
        raise Fail(f"{path} 业务失败 code={payload.get('code')} {payload.get('message')}")
    return payload.get("data")


def login() -> dict:
    mobile = "139" + f"{int(time.time() * 1000) % 100000000:08d}"
    sent = call("POST", "/api/auth/sms", body={"mobile": mobile})
    if sent.get("code") != 200:
        raise Fail(f"发验证码失败 code={sent.get('code')} {sent.get('message')}")
    signed = call("POST", "/api/auth/login", body={"mobile": mobile, "captcha": SMS_CODE})
    data = ok(signed, "/api/auth/login")
    if not data or not data.get("token"):
        raise Fail("登录未返回 token")
    return data


def send(token: str, text: str) -> dict:
    body = {"text": text, "messageId": str(uuid.uuid4()), "serviceType": 1}
    return ok(call("POST", "/api/consultations/messages", token=token, body=body), "/api/consultations/messages")


def scan_csv() -> tuple[dict, dict[str, list[dict]]]:
    census = {
        "rows": 0,
        "n6_message": 0,
        "certainty": Counter(),
        "source": Counter(),
        "reason": Counter(),
        "safety": Counter(),
        "gold_hit": Counter(),
        "oral_gold_miss": 0,
        "oral_certain_wrong": 0,
        "model_unavailable": 0,
    }
    buckets: dict[str, list[dict]] = {name: [] for name in BUCKET_CAP}
    seen: dict[str, set[str]] = defaultdict(set)
    with CSV_PATH.open(encoding="utf-8-sig", newline="") as handle:
        for row in csv.DictReader(handle):
            census["rows"] += 1
            census["certainty"][row.get("expect_route_certainty", "")] += 1
            census["source"][row.get("expect_route_source", "")] += 1
            census["reason"][row.get("expect_route_reason", "")] += 1
            census["safety"][row.get("expect_safety_status", "")] += 1
            census["gold_hit"][row.get("gold_hit", "")] += 1
            suite = row.get("suite") or ""
            oral = suite.startswith("S19") or suite.startswith("S20") or suite.startswith("S21") or suite.startswith("S25")
            gold = (row.get("gold_specialty") or "").strip()
            got = (row.get("expect_specialty") or "").strip()
            if oral and gold and got and gold != got:
                census["oral_gold_miss"] += 1
                if row.get("expect_route_certainty") == "CERTAIN":
                    census["oral_certain_wrong"] += 1
            if row.get("expect_route_reason") == "MODEL_UNAVAILABLE":
                census["model_unavailable"] += 1
            if row.get("action") != "MESSAGE" or row.get("node") not in ("N6", "N1", "N2"):
                continue
            census["n6_message"] += 1
            text = (row.get("input") or "").strip()
            if not text:
                continue
            item = {
                "case_id": row["case_id"],
                "suite": suite,
                "input": text,
                "expect_status": int(row["expect_status_code"] or 0),
                "expect_source": row.get("expect_route_source") or "",
                "expect_reason": row.get("expect_route_reason") or "",
                "expect_certainty": row.get("expect_route_certainty") or "",
                "expect_specialty": got,
                "gold_specialty": gold,
                "gold_hit": row.get("gold_hit") == "1",
                "expect_safety": row.get("expect_safety_status") or "",
            }
            key = text
            if row.get("expect_safety_status") == "URGENT" and key not in seen["safety_urgent"]:
                buckets["safety_urgent"].append(item)
                seen["safety_urgent"].add(key)
            if (
                row.get("node") == "N6"
                and row.get("expect_route_certainty") == "CERTAIN"
                and row.get("expect_route_source") == "RULE"
                and row.get("expect_status_code") == "4"
                and row.get("gold_hit") == "1"
                and key not in seen["rule_ok"]
            ):
                buckets["rule_ok"].append(item)
                seen["rule_ok"].add(key)
            if (
                row.get("node") == "N6"
                and row.get("expect_route_reason") == "MODEL_UNAVAILABLE"
                and key not in seen["rule_cant"]
            ):
                buckets["rule_cant"].append(item)
                seen["rule_cant"].add(key)
            if oral and gold and got and gold != got and row.get("expect_route_certainty") == "CERTAIN":
                if key not in seen["oral_certain_wrong"]:
                    buckets["oral_certain_wrong"].append(item)
                    seen["oral_certain_wrong"].add(key)
            if oral and row.get("expect_route_certainty") == "UNCERTAIN":
                if key not in seen["oral_uncertain"]:
                    buckets["oral_uncertain"].append(item)
                    seen["oral_uncertain"].add(key)
    rng = random.Random(SEED)
    sampled = {}
    for name, cap in BUCKET_CAP.items():
        pool = buckets[name]
        rng.shuffle(pool)
        sampled[name] = pool[:cap]
        census[f"pool_{name}"] = len(pool)
        census[f"sample_{name}"] = len(sampled[name])
    return census, sampled


def run_one(token: str, bucket: str, item: dict) -> dict:
    started = time.perf_counter()
    error = ""
    view: dict = {}
    try:
        view = send(token, item["input"])
    except Fail as exc:
        error = str(exc)[:300]
    elapsed_ms = int((time.perf_counter() - started) * 1000)
    card = (view or {}).get("card") or {}
    status = (view or {}).get("status")
    source = card.get("departmentSource") or ""
    specialty = card.get("specialty") or ""
    gold = item["gold_specialty"]
    reached_model = source in ("MODEL", "FALLBACK")
    if bucket == "rule_ok":
        expect_source = "RULE"
        expect_model = False
    elif bucket in ("rule_cant", "oral_uncertain"):
        expect_source = "MODEL"
        expect_model = True
    elif bucket == "oral_certain_wrong":
        expect_source = "RULE"
        expect_model = False
    elif bucket == "safety_urgent":
        expect_source = ""
        expect_model = False
    else:
        expect_source = item["expect_source"]
        expect_model = item["expect_certainty"] != "CERTAIN"
    gold_ok = bool(gold) and specialty == gold
    return {
        "bucket": bucket,
        "case_id": item["case_id"],
        "suite": item["suite"],
        "input": item["input"][:80],
        "elapsed_ms": elapsed_ms,
        "error": error,
        "status": status,
        "source": source,
        "specialty": specialty,
        "gold_specialty": gold,
        "expect_specialty": item["expect_specialty"],
        "expect_reason": item["expect_reason"],
        "prompt": ((view or {}).get("prompt") or "")[:80],
        "reached_model": reached_model,
        "expect_model": expect_model,
        "source_ok": (source == expect_source) if expect_source else True,
        "gold_ok": gold_ok if gold else None,
        "urgent_ok": status == URGENT if bucket == "safety_urgent" else None,
        "card_ready": status == CARD_READY,
    }


def summarize(rows: list[dict]) -> dict:
    by = defaultdict(list)
    for row in rows:
        by[row["bucket"]].append(row)
    out = {}
    for bucket, items in by.items():
        n = len(items)
        errors = sum(1 for x in items if x["error"])
        model_hit = sum(1 for x in items if x["reached_model"])
        source_ok = sum(1 for x in items if x["source_ok"] and not x["error"])
        gold_n = sum(1 for x in items if x["gold_ok"] is not None and not x["error"] and x["card_ready"])
        gold_ok = sum(1 for x in items if x["gold_ok"] is True)
        urgent_ok = sum(1 for x in items if x["urgent_ok"] is True)
        sources = Counter(x["source"] or "(none)" for x in items if not x["error"])
        statuses = Counter(str(x["status"]) for x in items if not x["error"])
        out[bucket] = {
            "n": n,
            "errors": errors,
            "model_hit": model_hit,
            "model_hit_rate": round(model_hit / n, 4) if n else 0,
            "source_ok": source_ok,
            "gold_n": gold_n,
            "gold_ok": gold_ok,
            "gold_rate": round(gold_ok / gold_n, 4) if gold_n else None,
            "urgent_ok": urgent_ok,
            "p50_ms": sorted(x["elapsed_ms"] for x in items)[n // 2] if n else 0,
            "p95_ms": sorted(x["elapsed_ms"] for x in items)[max(0, int(n * 0.95) - 1)] if n else 0,
            "sources": dict(sources),
            "statuses": dict(statuses),
        }
    return out


def run_full_chain(token: str, patient_id: str) -> dict:
    text = "女，32岁，干咳半年，运动后加重"
    view = send(token, text)
    if view.get("status") != CARD_READY:
        raise Fail(f"出卡失败 status={view.get('status')} prompt={view.get('prompt')}")
    card = view.get("card") or {}
    cid = view["consultationId"]
    ok(
        call("POST", "/api/consultations/patient", token=token, body={"consultationId": cid, "patientId": patient_id}),
        "bind",
    )
    confirmed = ok(
        call("POST", "/api/consultations/confirmation", token=token, body={"consultationId": cid}),
        "confirm",
    )
    if confirmed.get("status") != CONFIRMED:
        raise Fail(f"确认后 status={confirmed.get('status')}")
    ordered = ok(
        call(
            "POST",
            "/api/consultations/orders",
            token=token,
            body={"consultationId": cid, "packageCode": 3, "consultMinutes": 15},
        ),
        "order",
    )
    order = ordered.get("order") or {}
    doctor = order.get("doctor") or {}
    if not doctor.get("id"):
        raise Fail("支付后没有派到医生")
    ended = ok(
        call("POST", "/api/consultations/end", token=token, body={"consultationId": cid, "reason": "ENDED"}),
        "end",
    )
    return {
        "specialty": card.get("specialty"),
        "source": card.get("departmentSource"),
        "doctor": doctor.get("name"),
        "matchSource": doctor.get("matchSource"),
        "ended": bool((ended.get("order") or {}).get("endedAt")),
    }


def main() -> int:
    print(f"网关 {BASE}")
    wait_port("127.0.0.1", 18082)
    wait_port("127.0.0.1", 18081)
    wait_port("127.0.0.1", 8080)
    print("扫描 15 万 CSV …")
    census, sampled = scan_csv()
    print(
        f"rows={census['rows']} MODEL_UNAVAILABLE={census['model_unavailable']} "
        f"oral_gold_miss={census['oral_gold_miss']} certain_wrong={census['oral_certain_wrong']}"
    )
    session = login()
    token = session["token"]
    patients = ok(call("GET", "/api/patients", token=token), "/api/patients") or []
    if patients:
        patient = patients[0]
    else:
        patient = ok(
            call(
                "POST",
                "/api/patients",
                token=token,
                body={"name": "张明", "gender": 2, "age": 32, "relation": "本人"},
            ),
            "/api/patients",
        )
    print(f"已登录 account={session['accountId']}")

    jobs = []
    for bucket, items in sampled.items():
        for item in items:
            jobs.append((bucket, item))
    print(f"实跑 {len(jobs)} 条分层样本 workers={WORKERS}")
    rows: list[dict] = []
    with ThreadPoolExecutor(max_workers=WORKERS) as pool:
        futures = [pool.submit(run_one, token, bucket, item) for bucket, item in jobs]
        done = 0
        for fut in as_completed(futures):
            rows.append(fut.result())
            done += 1
            if done % 20 == 0 or done == len(futures):
                print(f"  {done}/{len(futures)}")

    chain = None
    chain_error = ""
    try:
        chain = run_full_chain(token, patient["id"])
        print(f"完整链路 {chain}")
    except Fail as exc:
        chain_error = str(exc)
        print(f"完整链路失败: {exc}")

    buckets = summarize(rows)
    misses = [
        x
        for x in rows
        if x["bucket"] in ("rule_cant", "oral_uncertain")
        and not x["error"]
        and x["card_ready"]
        and x["gold_ok"] is False
    ][:25]
    no_model = [
        x
        for x in rows
        if x["bucket"] in ("rule_cant", "oral_uncertain") and not x["error"] and not x["reached_model"]
    ][:25]
    certain_blocked = [
        x for x in rows if x["bucket"] == "oral_certain_wrong" and not x["error"] and not x["reached_model"]
    ]
    report = {
        "generated_at": time.strftime("%Y-%m-%d %H:%M:%S"),
        "base": BASE,
        "census": {
            "rows": census["rows"],
            "certainty": dict(census["certainty"]),
            "source": dict(census["source"]),
            "reason_top": dict(census["reason"].most_common(10)),
            "safety": dict(census["safety"]),
            "model_unavailable": census["model_unavailable"],
            "oral_gold_miss": census["oral_gold_miss"],
            "oral_certain_wrong": census["oral_certain_wrong"],
            "pools": {k: census.get(f"pool_{k}") for k in BUCKET_CAP},
            "samples": {k: census.get(f"sample_{k}") for k in BUCKET_CAP},
        },
        "live": buckets,
        "full_chain": chain,
        "full_chain_error": chain_error,
        "examples": {
            "rule_cant_no_model": [
                {"input": x["input"], "source": x["source"], "specialty": x["specialty"], "gold": x["gold_specialty"]}
                for x in no_model
            ],
            "rule_cant_wrong_gold": [
                {"input": x["input"], "source": x["source"], "specialty": x["specialty"], "gold": x["gold_specialty"]}
                for x in misses
            ],
            "oral_certain_wrong_blocked": [
                {"input": x["input"], "source": x["source"], "specialty": x["specialty"], "gold": x["gold_specialty"]}
                for x in certain_blocked[:15]
            ],
        },
        "rows": rows,
    }
    OUT_JSON.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(buckets, ensure_ascii=False, indent=2))
    print(f"报告 {OUT_JSON}")
    return 0 if not chain_error else 1


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Fail as error:
        print(f"失败: {error}", file=sys.stderr)
        sys.exit(1)
