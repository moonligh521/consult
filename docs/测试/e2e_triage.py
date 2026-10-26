# -*- coding: utf-8 -*-
"""挂号完整链路：经网关登录 → 导诊出卡 → 绑就诊人 → 确认 → 演示支付 → 派医生。

不读、不打印密钥。默认打本机网关 8080。

用法：
    python docs/测试/e2e_triage.py

先起三个进程（仓库根目录、JAVA_HOME 指向 JDK 21）：
    mvn -pl consult-gateway,consult-identity/consult-identity-service,consult-registration-agent/consult-registration-agent-service -am "-DskipTests" package
    java -jar consult-identity/consult-identity-service/target/consult-identity-service-0.1.0-SNAPSHOT.jar
    java -jar consult-registration-agent/consult-registration-agent-service/target/consult-registration-agent-service-0.1.0-SNAPSHOT.jar
    java -jar consult-gateway/target/consult-gateway-0.1.0-SNAPSHOT.jar
"""

from __future__ import annotations

import json
import os
import socket
import sys
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BASE = os.environ.get("CONSULT_GATEWAY", "http://127.0.0.1:8080").rstrip("/")
SMS_CODE = os.environ.get("AUTH_SMS_DEV_CODE", "000000")
TIMEOUT = 20
DEMO_DOCTORS = ("周宁", "赵衡", "孙悦", "王启明")

CARD_READY = 4
CONFIRMED = 5
URGENT = 3
PAY_PAID = 2


class Fail(Exception):
    pass


def load_mysql() -> dict:
    text = (ROOT / "config" / "application-local.yml").read_text(encoding="utf-8")
    url = None
    user = None
    password = None
    for line in text.splitlines():
        stripped = line.strip()
        if stripped.startswith("url:") and "jdbc:mysql://" in stripped:
            raw = stripped.split("jdbc:mysql://", 1)[1]
            hostport, rest = raw.split("/", 1)
            host, port = hostport.split(":")
            database = rest.split("?", 1)[0]
            url = (host, int(port), database)
        elif stripped.startswith("username:") and user is None:
            user = stripped.split(":", 1)[1].strip().strip('"').strip("'")
        elif stripped.startswith("password:") and password is None:
            password = stripped.split(":", 1)[1].strip().strip('"').strip("'")
    if not url or not user or password is None:
        raise Fail("application-local.yml 里没有完整的 MySQL 连接")
    return {"host": url[0], "port": url[1], "database": url[2], "user": user, "password": password}


def reset_demo_occupancy() -> None:
    """问诊结束还没有释放占坑。演示库并发=3 时，旧单会把对口医生挤出号源。"""
    try:
        import pymysql
    except ImportError as error:
        raise Fail("需要 pymysql 才能清演示医生占坑") from error
    cfg = load_mysql()
    conn = pymysql.connect(
        host=cfg["host"],
        port=cfg["port"],
        user=cfg["user"],
        password=cfg["password"],
        database=cfg["database"],
        charset="utf8mb4",
    )
    try:
        with conn.cursor() as cursor:
            cursor.execute(
                "UPDATE consult_doctor SET in_flight = 0 WHERE name IN (%s, %s, %s, %s) AND is_deleted = 0",
                DEMO_DOCTORS,
            )
            changed = cursor.rowcount
        conn.commit()
    finally:
        conn.close()
    print(f"已清演示医生占坑 {changed} 人")


def wait_port(host: str, port: int, seconds: float = 90) -> None:
    deadline = time.time() + seconds
    last = None
    while time.time() < deadline:
        try:
            with socket.create_connection((host, port), timeout=2):
                return
        except OSError as error:
            last = error
            time.sleep(0.5)
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
    try:
        payload = json.loads(raw)
    except json.JSONDecodeError as error:
        raise Fail(f"{method} {path} 非 JSON: {raw[:400]}") from error
    if not isinstance(payload, dict):
        raise Fail(f"{method} {path} 返回不是对象")
    return payload


def ok(payload: dict, path: str) -> dict:
    if payload.get("code") != 200:
        raise Fail(f"{path} 业务失败 code={payload.get('code')} {payload.get('message')}")
    return payload.get("data")


def login() -> dict:
    mobile = "138" + f"{int(time.time()) % 100000000:08d}"
    sent = call("POST", "/api/auth/sms", body={"mobile": mobile})
    if sent.get("code") != 200:
        raise Fail(f"发验证码失败 code={sent.get('code')} {sent.get('message')}")
    signed = call("POST", "/api/auth/login", body={"mobile": mobile, "captcha": SMS_CODE})
    data = ok(signed, "/api/auth/login")
    if not data or not data.get("token") or not data.get("accountId"):
        raise Fail("登录未返回 token/accountId")
    return data


def ensure_patient(token: str) -> dict:
    listed = ok(call("GET", "/api/patients", token=token), "/api/patients") or []
    if listed:
        return listed[0]
    created = ok(
        call(
            "POST",
            "/api/patients",
            token=token,
            body={"name": "张明", "gender": 2, "age": 32, "relation": "本人"},
        ),
        "/api/patients",
    )
    if not created or not created.get("id"):
        raise Fail("新建就诊人未返回 id")
    return created


def send(token: str, text: str, consultation_id: str | None = None) -> dict:
    body = {"text": text, "messageId": str(uuid.uuid4()), "serviceType": 1}
    if consultation_id:
        body["consultationId"] = consultation_id
    return ok(call("POST", "/api/consultations/messages", token=token, body=body), "/api/consultations/messages")


def expect_fail(payload: dict, code: int, action: str) -> None:
    if payload.get("code") != code:
        raise Fail(f"{action} 期望业务码 {code}，实际 {payload.get('code')} {payload.get('message')}")


def run_paid(token: str, patient_id: str, text: str, expect: dict) -> dict:
    view = send(token, text)
    if view.get("status") != CARD_READY:
        raise Fail(f"出卡失败 status={view.get('status')} prompt={view.get('prompt')}")
    card = view.get("card") or {}
    if card.get("specialty") != expect["specialty"]:
        raise Fail(f"二级科期望 {expect['specialty']}，实际 {card.get('specialty')}")
    if card.get("department") != expect["department"]:
        raise Fail(f"一级科期望 {expect['department']}，实际 {card.get('department')}")
    if card.get("departmentSource") != expect["source"]:
        raise Fail(f"科室来源期望 {expect['source']}，实际 {card.get('departmentSource')}")
    consultation_id = view["consultationId"]
    bound = ok(
        call(
            "POST",
            "/api/consultations/patient",
            token=token,
            body={"consultationId": consultation_id, "patientId": patient_id},
        ),
        "/api/consultations/patient",
    )
    if (bound.get("card") or {}).get("patientId") != patient_id:
        raise Fail("绑就诊人后卡片没有 patientId")
    confirmed = ok(
        call(
            "POST",
            "/api/consultations/confirmation",
            token=token,
            body={"consultationId": consultation_id},
        ),
        "/api/consultations/confirmation",
    )
    if confirmed.get("status") != CONFIRMED:
        raise Fail(f"确认后 status={confirmed.get('status')}")
    if not confirmed.get("packages"):
        raise Fail("确认后没有咨询档位")
    ordered = ok(
        call(
            "POST",
            "/api/consultations/orders",
            token=token,
            body={"consultationId": consultation_id, "packageCode": 3, "consultMinutes": 15},
        ),
        "/api/consultations/orders",
    )
    order = ordered.get("order") or {}
    if order.get("packageCode") != 3:
        raise Fail(f"档位不是三甲医生: {order.get('packageCode')}")
    pay_status = order.get("payStatus")
    if pay_status not in (PAY_PAID, {"code": PAY_PAID}):
        raise Fail(f"支付状态不是已支付: {pay_status}")
    doctor = order.get("doctor")
    if not doctor or not doctor.get("id") or not doctor.get("name"):
        raise Fail("支付后没有派到医生")
    if expect.get("doctorName") and doctor.get("name") != expect["doctorName"]:
        raise Fail(f"医生期望 {expect['doctorName']}，实际 {doctor.get('name')} source={doctor.get('matchSource')}")
    if expect.get("skill") and expect["skill"] not in (doctor.get("skills") or ""):
        raise Fail(f"医生擅长不含 {expect['skill']}: {doctor.get('skills')}")
    if expect.get("matchSource") and doctor.get("matchSource") != expect["matchSource"]:
        raise Fail(f"matchSource 期望 {expect['matchSource']}，实际 {doctor.get('matchSource')}")
    if doctor.get("specialty") != expect["specialty"] and doctor.get("specialty") != expect.get("doctorSpecialty"):
        raise Fail(f"医生二级科 {doctor.get('specialty')} 对不上卡片 {expect['specialty']}")
    again = ok(
        call(
            "POST",
            "/api/consultations/orders",
            token=token,
            body={"consultationId": consultation_id, "packageCode": 3},
        ),
        "/api/consultations/orders 幂等",
    )
    if (again.get("order") or {}).get("orderId") != order.get("orderId"):
        raise Fail("重复支付没有返回同一订单")
    if not order.get("endsAt"):
        raise Fail("支付后没有问诊到期时间")
    if order.get("consultMinutes") not in (None, 15):
        raise Fail(f"默认时长不是 15 分钟: {order.get('consultMinutes')}")
    ended = ok(
        call(
            "POST",
            "/api/consultations/end",
            token=token,
            body={"consultationId": consultation_id, "reason": "ENDED"},
        ),
        "/api/consultations/end",
    )
    ended_order = ended.get("order") or {}
    if not ended_order.get("endedAt"):
        raise Fail("结束问诊没有 endedAt")
    again_end = ok(
        call(
            "POST",
            "/api/consultations/end",
            token=token,
            body={"consultationId": consultation_id, "reason": "ENDED"},
        ),
        "/api/consultations/end 幂等",
    )
    if (again_end.get("order") or {}).get("endedAt") != ended_order.get("endedAt"):
        raise Fail("重复结束没有保持 endedAt")
    return {
        "consultationId": consultation_id,
        "orderId": order.get("orderId"),
        "doctor": doctor.get("name"),
        "matchSource": doctor.get("matchSource"),
        "skills": doctor.get("skills"),
        "specialty": card.get("specialty"),
        "source": card.get("departmentSource"),
        "endedAt": ended_order.get("endedAt"),
    }


def run_urgent(token: str) -> None:
    view = send(token, "45岁，胸痛两小时")
    if view.get("status") != URGENT:
        raise Fail(f"胸痛未急症分流 status={view.get('status')} prompt={view.get('prompt')}")
    consultation_id = view["consultationId"]
    expect_fail(
        call(
            "POST",
            "/api/consultations/confirmation",
            token=token,
            body={"consultationId": consultation_id},
        ),
        1103,
        "急症确认",
    )
    expect_fail(
        call(
            "POST",
            "/api/consultations/orders",
            token=token,
            body={"consultationId": consultation_id, "packageCode": 1},
        ),
        1103,
        "急症下单",
    )


def main() -> int:
    print(f"网关 {BASE}")
    wait_port("127.0.0.1", 18082)
    wait_port("127.0.0.1", 18081)
    wait_port("127.0.0.1", 8080)
    reset_demo_occupancy()
    session = login()
    token = session["token"]
    patient = ensure_patient(token)
    print(f"已登录 account={session['accountId']} patient={patient['id']}")

    cough = run_paid(
        token,
        patient["id"],
        "女，32岁，干咳半年，运动后加重",
        {
            "specialty": "呼吸内科",
            "department": 1,
            "source": "RULE",
            "doctorName": "周宁",
            "skill": "干咳",
            "matchSource": "SKILL_TEXT",
        },
    )
    print(
        f"干咳 → {cough['specialty']}/{cough['source']} → {cough['doctor']} "
        f"{cough['matchSource']} order={cough['orderId']}"
    )

    htn = run_paid(
        token,
        patient["id"],
        "女，45岁，高血压三年，头胀两天",
        {
            "specialty": "普内科",
            "department": 1,
            "source": "RULE",
            "doctorName": "赵衡",
            "skill": "高血压",
            "matchSource": "SKILL_CANON",
        },
    )
    print(
        f"高血压 → {htn['specialty']}/{htn['source']} → {htn['doctor']} "
        f"{htn['matchSource']} order={htn['orderId']}"
    )

    run_urgent(token)
    print("胸痛 → 急症，确认和下单均 1103")
    print("完整链路通过")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Fail as error:
        print(f"失败: {error}", file=sys.stderr)
        sys.exit(1)
