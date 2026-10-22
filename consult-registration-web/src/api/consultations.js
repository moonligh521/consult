import { http } from './http'

/** 患者发一句话。第一次不传 consultationId。 */
export function sendMessage(payload) {
  return http.post('/api/consultations/messages', payload)
}

/** 改卡片上已填的项。 */
export function patchCard(payload) {
  return http.patch('/api/consultations/card', payload)
}

/** 确认当前卡片。 */
export function confirmCard(consultationId) {
  return http.post('/api/consultations/confirmation', { consultationId })
}

/** 卡片确认后的四档咨询。 */
export function listPackages() {
  return http.get('/api/consultations/packages')
}

/** 选择档位并记下演示订单。 */
export function placeOrder(consultationId, packageCode) {
  return http.post('/api/consultations/orders', { consultationId, packageCode })
}
