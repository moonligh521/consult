// 1000 并发咨询压测。每个虚拟用户：登录一次 → 反复走「开一轮咨询 → 补充两句」。
//
// 前提（只在压测环境）：
//   身份服务 AUTH_SMS_DEV_CODE=000000；挂号服务 MODEL_BASE_URL 指向 loadtest/wiremock（模拟 1.2s 中位延迟的模型）；
//   网关限流按账号计，每个 VU 用不同手机号，不会被 consult-write 档误伤。
//
// 运行：
//   k6 run -e BASE_URL=http://gateway:8080 loadtest/k6/consult-1000.js
//   THINK_MS=0 时每个 VU 不停发，在途请求 ≈ VU 数，用来压极限；默认 3000ms 模拟真人打字间隔。
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const BASE = __ENV.BASE_URL || 'http://127.0.0.1:8080';
const VUS = Number(__ENV.VUS || 1000);
const THINK_MS = Number(__ENV.THINK_MS || 3000);
const CAPTCHA = __ENV.CAPTCHA || '000000';

const rateLimited = new Counter('consult_rate_limited');
const shed = new Counter('consult_shed');
const businessFail = new Counter('consult_business_fail');
const messageLatency = new Trend('consult_message_latency', true);

export const options = {
  scenarios: {
    consult: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '2m', target: VUS },   // 爬坡，观察 HPA 扩容
        { duration: '10m', target: VUS },  // 稳态
        { duration: '1m', target: 0 },
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    consult_message_latency: ['p(95)<5000', 'p(99)<8000'],
    consult_shed: ['count<100'],
    checks: ['rate>0.99'],
  },
};

const JSON_HEADERS = { 'Content-Type': 'application/json' };
const COMPLAINTS = [
  '女，32岁，干咳半年，运动后加重',
  '男，45岁，胃胀反酸两周，饭后明显',
  '女，28岁，反复头痛一个月，太阳穴胀痛',
  '男，60岁，膝关节疼三个月，上下楼加重',
  '女，35岁，皮肤起红疹瘙痒一周',
];
const FOLLOW_UPS = ['没有发烧', '晚上更明显', '之前没看过医生', '没有药物过敏'];

let token = null;

function mobileOf(vu) {
  return '139' + String(vu).padStart(8, '0');
}

function login() {
  const mobile = mobileOf(__VU);
  const res = http.post(`${BASE}/api/auth/login`, JSON.stringify({ mobile, captcha: CAPTCHA }), {
    headers: JSON_HEADERS,
    tags: { name: 'login' },
  });
  const body = res.json();
  check(res, { 'login ok': () => res.status === 200 && body && body.data && body.data.token });
  return body && body.data ? body.data.token : null;
}

function send(consultationId, text) {
  const res = http.post(
    `${BASE}/api/consultations/messages`,
    JSON.stringify({ consultationId, messageId: uuidv4(), text }),
    { headers: { ...JSON_HEADERS, Authorization: `Bearer ${token}` }, tags: { name: 'message' } },
  );
  messageLatency.add(res.timings.duration);
  if (res.status === 429) {
    rateLimited.add(1);
    return null;
  }
  if (res.status === 503) {
    shed.add(1);
    return null;
  }
  const body = res.json();
  // 业务错误走 HTTP 200 + code；安全停止、转人工也算正常业务结果
  const ok = check(res, { 'message 200': () => res.status === 200 && body && typeof body.code === 'number' });
  // 1111 安全兜底、1127 单飞拒绝在压测里要接近 0：前者多说明模型配额或超时没配够，后者说明客户端在重复提交
  if (ok && body.code !== 200) {
    businessFail.add(1, { code: String(body.code) });
  }
  return body && body.data ? body.data.consultationId : null;
}

function think() {
  if (THINK_MS > 0) {
    sleep((THINK_MS * (0.5 + Math.random())) / 1000);
  }
}

export default function () {
  if (!token) {
    token = login();
    if (!token) {
      sleep(1);
      return;
    }
  }
  const first = COMPLAINTS[Math.floor(Math.random() * COMPLAINTS.length)];
  const id = send(null, first);
  think();
  if (!id) {
    return;
  }
  for (let i = 0; i < 2; i++) {
    send(id, FOLLOW_UPS[Math.floor(Math.random() * FOLLOW_UPS.length)]);
    think();
  }
}
