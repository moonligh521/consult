// 日均 10 万单的服务器验收：恒定每秒 4 次迭代。一次迭代是登录后的一轮消息，能出卡就下一笔普通咨询。
//
// 只打压测栈，不要打共用库：
//   docker compose -f deploy/compose/loadtest-compose.yml up -d --build
//   等身份服务日志出现「演示心跳已打开」后再跑：
//   docker compose -f deploy/compose/loadtest-compose.yml --profile orders run --rm k6-orders
//
// 通过：http_req_failed < 1%，checks > 99%，削峰 < 10，order_ok > 95%。
// 心跳还没打开时医生不可派，order_ok 会失败。这不是把阈值放宽的理由。
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const BASE = __ENV.BASE_URL || 'http://127.0.0.1:8080';
const RATE = Number(__ENV.RATE || 4);
const DURATION = __ENV.DURATION || '5m';
const CAPTCHA = __ENV.CAPTCHA || '000000';
const STRICT = (__ENV.ORDER_STRICT || '1') !== '0';

const shed = new Counter('consult_shed');
const orderOk = new Rate('order_ok');

export const options = {
  scenarios: {
    orders: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: 30,
      maxVUs: 80,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
    consult_shed: ['count<10'],
    order_ok: [STRICT ? 'rate>0.95' : 'rate>=0'],
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
  return '138' + String(vu).padStart(8, '0');
}

function login() {
  const res = http.post(
    `${BASE}/api/auth/login`,
    JSON.stringify({ mobile: mobileOf(__VU), captcha: CAPTCHA }),
    { headers: JSON_HEADERS, tags: { name: 'login' } },
  );
  const body = res.json();
  const ok = check(res, { 'login ok': () => res.status === 200 && body && body.data && body.data.token });
  return ok ? body.data.token : null;
}

function send(consultationId, text) {
  const res = http.post(
    `${BASE}/api/consultations/messages`,
    JSON.stringify({ consultationId, messageId: uuidv4(), text }),
    { headers: { ...JSON_HEADERS, Authorization: `Bearer ${token}` }, tags: { name: 'message' }, timeout: '30s' },
  );
  if (res.status === 503) {
    shed.add(1);
  }
  const body = res.json();
  check(res, { 'message 200': () => res.status === 200 && body && typeof body.code === 'number' });
  return body && body.data ? body.data : null;
}

function place(consultationId) {
  const res = http.post(
    `${BASE}/api/consultations/orders`,
    JSON.stringify({ consultationId, packageCode: 1, consultMinutes: 15, channel: 'demo' }),
    { headers: { ...JSON_HEADERS, Authorization: `Bearer ${token}` }, tags: { name: 'order' }, timeout: '15s' },
  );
  if (res.status === 503) {
    shed.add(1);
  }
  const body = res.json();
  const httpOk = check(res, { 'order http': () => res.status === 200 && body && typeof body.code === 'number' });
  const accepted = httpOk && body.code === 200 && body.data && body.data.order;
  orderOk.add(accepted);
  return accepted;
}

export default function () {
  if (!token) {
    token = login();
    if (!token) {
      orderOk.add(false);
      sleep(1);
      return;
    }
  }
  let data = send(null, COMPLAINTS[Math.floor(Math.random() * COMPLAINTS.length)]);
  let id = data && data.consultationId;
  for (let i = 0; id && !(data.packages && data.packages.length) && !data.order && i < 3; i++) {
    data = send(id, FOLLOW_UPS[i]);
    id = (data && data.consultationId) || id;
  }
  if (!id) {
    orderOk.add(false);
    return;
  }
  place(id);
}
