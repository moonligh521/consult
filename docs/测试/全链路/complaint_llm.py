# -*- coding: utf-8 -*-
"""评测副本的主诉大模型：与 Java DeepSeekComplaintModel 共用提示词，缺密钥时明确失败而不是悄悄关模型。"""
from __future__ import annotations

import atexit
import hashlib
import json
import os
import pathlib
import sys
import time
import urllib.error
import urllib.request
from typing import List, Optional, Sequence

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parents[2]
PROMPT_PATH = (
    ROOT
    / "consult-registration-agent"
    / "consult-registration-agent-service"
    / "src"
    / "main"
    / "resources"
    / "model"
    / "complaint-prompt.txt"
)
CACHE_PATH = HERE / ".complaint-llm-cache.json"
FAIL_LOG_PATH = HERE / ".complaint-llm-fails.jsonl"
PROMPT_VERSION = "complaint-v3.1"
MAX_TOKENS = 2000
TIMEOUT_SEC = 30
BASE_URL = os.environ.get("MODEL_BASE_URL", "https://api.deepseek.com").rstrip("/")
MODEL_NAME = os.environ.get("MODEL_NAME", "deepseek-v4-flash")

_PROMPT = PROMPT_PATH.read_text(encoding="utf-8")
_CACHE: Optional[dict] = None
_CACHE_DIRTY = 0
_LAST_STATUS = "UNAVAILABLE"
_STATS = {"calls": 0, "cache": 0, "ok": 0, "fail": 0, "fail_timeout": 0, "fail_invalid": 0, "fail_unavailable": 0}


def _flush_cache() -> None:
    global _CACHE_DIRTY
    if _CACHE is None or _CACHE_DIRTY <= 0:
        return
    _save_cache(force=True)


atexit.register(_flush_cache)


class LiveModelRequired(RuntimeError):
    pass


def api_key() -> str:
    env = (os.environ.get("DEEPSEEK_API_KEY") or "").strip()
    if env:
        return env
    import re
    for path in (
        ROOT / "config" / "application-local.yml",
        ROOT / "consult-registration-agent" / "consult-registration-agent-service"
        / "src" / "main" / "resources" / "application-local.yml",
    ):
        if not path.exists():
            continue
        text = path.read_text(encoding="utf-8")
        match = re.search(r'(?m)^\s*api-key:\s*"([^"]+)"', text) or re.search(r'(?m)^\s*api-key:\s*(\S+)', text)
        if not match:
            continue
        value = match.group(1).strip().strip('"')
        if value and value not in {"replace-with-deepseek-key", "${DEEPSEEK_API_KEY:}"}:
            return value
    return ""


def live_enabled() -> bool:
    flag = (os.environ.get("COMPLAINT_LLM_OFF") or "").strip().lower()
    if flag in {"1", "true", "yes", "on"}:
        return False
    return bool(api_key())


def require_live_model() -> None:
    if not live_enabled():
        raise LiveModelRequired(
            "未配置 DEEPSEEK_API_KEY。该走模型的句子不能关模型；"
            "请设置环境变量或在 application-local.yml 写入 model.api-key 后重跑生成/评分。"
        )


def last_status() -> str:
    return _LAST_STATUS


def stats() -> dict:
    return dict(_STATS)


def system_prompt(allow_terms: Sequence[str], candidates: Sequence[str]) -> str:
    return (
        _PROMPT
        .replace("{{ALLOW_TERMS}}", "、".join(allow_terms or []))
        .replace("{{CANDIDATES}}", "、".join(candidates or []))
    )


def _cache() -> dict:
    global _CACHE
    if _CACHE is None:
        if CACHE_PATH.exists():
            _CACHE = json.loads(CACHE_PATH.read_text(encoding="utf-8"))
        else:
            _CACHE = {}
    return _CACHE


def _save_cache(force: bool = False) -> None:
    global _CACHE_DIRTY
    _CACHE_DIRTY += 1
    if not force and _CACHE_DIRTY < 20:
        return
    CACHE_PATH.write_text(json.dumps(_cache(), ensure_ascii=False), encoding="utf-8")
    _CACHE_DIRTY = 0


def _cache_key(
    description: str,
    age: Optional[int],
    gender: Optional[int],
    candidates: Sequence[str],
    catalog_version: str,
) -> str:
    compact = "".join(ch for ch in (description or "") if not ch.isspace()).lower()
    band = "" if age is None else ("child" if age < 14 else "adult" if age < 60 else "senior")
    gender_name = {1: "MALE", 2: "FEMALE"}.get(gender or 0, "")
    raw = f"{compact}|{band}|{gender_name}|{','.join(candidates)}"
    digest = hashlib.sha256(raw.encode("utf-8")).hexdigest()
    return f"{PROMPT_VERSION}:{catalog_version}:{digest}"


def interpret(
    description: str,
    age: Optional[int],
    gender: Optional[int],
    allow_terms: Sequence[str],
    candidates: Sequence[str],
    catalog_version: str = "",
) -> Optional[dict]:
    """成功返回 {specialty, evidence, terms, model_name}；格式不合格或调用失败返回 None。"""
    global _LAST_STATUS
    require_live_model()
    key = _cache_key(description, age, gender, candidates, catalog_version)
    cached = _cache().get(key)
    if cached:
        _STATS["cache"] += 1
        _LAST_STATUS = "SUCCESS"
        return cached if _terms_allowed(cached.get("terms") or [], allow_terms) else None
    gender_name = {1: "MALE", 2: "FEMALE"}.get(gender or 0, "未知")
    user = f"age={age if age is not None else '未知'} gender={gender_name}\n主诉：{description}"
    _STATS["calls"] += 1
    payload, chat_status = _chat(system_prompt(allow_terms, candidates), user)
    parsed = _parse(payload, allow_terms, candidates, description)
    if parsed is None and payload:
        payload, chat_status = _chat(
            system_prompt(allow_terms, candidates),
            user + "\n只输出合法 JSON。症状不要填进 terms，terms 只能是允许病名或 []。",
        )
        parsed = _parse(payload, allow_terms, candidates, description)
    if parsed is None:
        _LAST_STATUS = chat_status if chat_status in ("TIMEOUT", "UNAVAILABLE") else (
            "INVALID_OUTPUT" if payload else "UNAVAILABLE"
        )
        _STATS["fail"] += 1
        if _LAST_STATUS == "TIMEOUT":
            _STATS["fail_timeout"] += 1
        elif _LAST_STATUS == "INVALID_OUTPUT":
            _STATS["fail_invalid"] += 1
        else:
            _STATS["fail_unavailable"] += 1
        _log_fail(description, _LAST_STATUS, payload)
        _log_progress()
        return None
    _cache()[key] = parsed
    _save_cache()
    _LAST_STATUS = "SUCCESS"
    _STATS["ok"] += 1
    _log_progress()
    return parsed


def _log_fail(description: str, status: str, payload: str) -> None:
    row = json.dumps({
        "status": status,
        "input": (description or "")[:200],
        "payload": (payload or "")[:500],
    }, ensure_ascii=False)
    with FAIL_LOG_PATH.open("a", encoding="utf-8") as handle:
        handle.write(row + "\n")


def _log_progress() -> None:
    total = _STATS["calls"] + _STATS["cache"]
    if total == 1 or total % 20 == 0:
        print(
            f"[complaint-llm] 实呼 {_STATS['calls']} 缓存 {_STATS['cache']} "
            f"成功 {_STATS['ok']} 失败 {_STATS['fail']}"
            f"（超时 {_STATS['fail_timeout']} 格式 {_STATS['fail_invalid']} 不可用 {_STATS['fail_unavailable']}）",
            file=sys.stderr,
            flush=True,
        )


def _terms_allowed(terms: Sequence[dict], allow_terms: Sequence[str]) -> bool:
    allow = set(allow_terms or [])
    if len(terms) > 2:
        return False
    return all(item.get("term") in allow for item in terms)


def _chat(system: str, user: str) -> tuple[str, str]:
    """返回 (正文, 状态)。超时只再打一次。"""
    body = json.dumps({
        "model": MODEL_NAME,
        "temperature": 0,
        "max_tokens": MAX_TOKENS,
        "response_format": {"type": "json_object"},
        "messages": [
            {"role": "system", "content": system},
            {"role": "user", "content": user},
        ],
    }, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(
        BASE_URL + "/chat/completions",
        data=body,
        headers={
            "Authorization": "Bearer " + api_key(),
            "Content-Type": "application/json; charset=utf-8",
        },
        method="POST",
    )
    status = "UNAVAILABLE"
    for attempt in range(2):
        try:
            with urllib.request.urlopen(request, timeout=TIMEOUT_SEC) as response:
                raw = json.loads(response.read().decode("utf-8"))
            content = (((raw.get("choices") or [{}])[0].get("message") or {}).get("content") or "")
            return content, "SUCCESS" if content else "INVALID_OUTPUT"
        except urllib.error.HTTPError:
            status = "UNAVAILABLE"
            break
        except (TimeoutError, urllib.error.URLError) as error:
            if _is_timeout(error) and attempt == 0:
                status = "TIMEOUT"
                time.sleep(0.4)
                continue
            status = "TIMEOUT" if _is_timeout(error) else "UNAVAILABLE"
            break
        except json.JSONDecodeError:
            status = "UNAVAILABLE"
            break
    return "", status


def _is_timeout(error: BaseException) -> bool:
    if isinstance(error, TimeoutError):
        return True
    reason = getattr(error, "reason", error)
    if isinstance(reason, TimeoutError):
        return True
    text = str(reason).lower()
    return "timed out" in text or "timeout" in text


def _parse(content: str, allow_terms: Sequence[str], candidates: Sequence[str], description: str) -> Optional[dict]:
    if not content:
        return None
    start, end = content.find("{"), content.rfind("}")
    if start < 0 or end < start:
        return None
    try:
        payload = json.loads(content[start:end + 1])
    except json.JSONDecodeError:
        return None
    terms = payload.get("terms")
    if terms is None:
        terms = []
    if not isinstance(terms, list):
        return None
    specialty = payload.get("specialty") or ""
    evidence = payload.get("specialtyEvidence") or ""
    if specialty not in set(candidates or []):
        return None
    if evidence and evidence not in (description or ""):
        return None
    allow = set(allow_terms or [])
    clean_terms = []
    for item in terms:
        term = (item or {}).get("term") or ""
        term_evidence = (item or {}).get("evidence") or ""
        if not term or term not in allow:
            continue
        if not term_evidence:
            continue
        if term_evidence not in (description or ""):
            continue
        clean_terms.append({"term": term, "evidence": term_evidence})
        if len(clean_terms) >= 2:
            break
    return {
        "specialty": specialty,
        "evidence": evidence,
        "terms": clean_terms,
        "model_name": MODEL_NAME,
        "prompt_version": PROMPT_VERSION,
    }
