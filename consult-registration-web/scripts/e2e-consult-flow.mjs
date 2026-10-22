/**
 * 打真实后端 18081：发病情 → 确认卡片 → 拉档位 → 下单 → 核对订单。
 * 用法：node scripts/e2e-consult-flow.mjs
 */
const BASE = process.env.CONSULT_API || 'http://127.0.0.1:18081'

async function call(method, path, body) {
  const response = await fetch(`${BASE}${path}`, {
    method,
    headers: { 'Content-Type': 'application/json; charset=utf-8' },
    body: body == null ? undefined : JSON.stringify(body),
  })
  if (!response.ok) {
    throw new Error(`${method} ${path} HTTP ${response.status}`)
  }
  return response.json()
}

function assert(ok, message) {
  if (!ok) {
    throw new Error(message)
  }
}

const packages = await call('GET', '/api/consultations/packages')
assert(packages.code === 200, `拉档位失败: ${packages.message}`)
assert(Array.isArray(packages.data) && packages.data.length === 4, '档位不是四档')
assert(packages.data[2].code === 3 && packages.data[2].name === '三甲医生', '三甲档位不对')

const created = await call('POST', '/api/consultations/messages', {
  text: '女，32岁，干咳半年，运动后加重',
})
assert(created.code === 200, `发消息失败: ${created.message}`)
assert(created.data.status === 4, `期望卡片待确认，实际 ${created.data.status}`)
assert(created.data.card?.department === 1, '科室应为内科')
const consultationId = created.data.consultationId

const confirmed = await call('POST', '/api/consultations/confirmation', { consultationId })
assert(confirmed.code === 200, `确认失败: ${confirmed.message}`)
assert(confirmed.data.status === 5, `期望已确认，实际 ${confirmed.data.status}`)
assert(confirmed.data.packages?.length === 4, '确认后应带回四档')
assert(confirmed.data.order == null, '确认后还不该有订单')

const ordered = await call('POST', '/api/consultations/orders', { consultationId, packageCode: 3 })
assert(ordered.code === 200, `下单失败: ${ordered.message}`)
assert(ordered.data.order?.packageCode === 3, '订单档位应为三甲')
assert(ordered.data.order?.payStatus === 2, '演示支付应为已支付')
assert(Number(ordered.data.order?.payAmount) === 6.9, `应付应为 6.9，实际 ${ordered.data.order?.payAmount}`)

const again = await call('POST', '/api/consultations/orders', { consultationId, packageCode: 3 })
assert(again.code === 200, `重复下单失败: ${again.message}`)
assert(again.data.order?.orderId === ordered.data.order.orderId, '同一档位应返回同一订单')

const other = await call('POST', '/api/consultations/orders', { consultationId, packageCode: 4 })
assert(other.code === 1117, `换档应 1117，实际 ${other.code}`)

const tooEarly = await call('POST', '/api/consultations/messages', {
  text: '女，32岁，干咳半年，运动后加重',
})
const earlyOrder = await call('POST', '/api/consultations/orders', {
  consultationId: tooEarly.data.consultationId,
  packageCode: 1,
})
assert(earlyOrder.code === 1115, `未确认下单应 1115，实际 ${earlyOrder.code}`)

const urgent = await call('POST', '/api/consultations/messages', { text: '胸痛' })
assert(urgent.data.status === 3, `胸痛应为急症，实际 ${urgent.data.status}`)
const urgentOrder = await call('POST', '/api/consultations/orders', {
  consultationId: urgent.data.consultationId,
  packageCode: 1,
})
assert(urgentOrder.code === 1103, `急症下单应 1103，实际 ${urgentOrder.code}`)

console.log(`e2e ok consultationId=${consultationId} orderId=${ordered.data.order.orderId}`)
