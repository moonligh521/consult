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

/** 选择档位并支付。后端直接返回支付成功。 */
export function placeOrder(consultationId, packageCode, payChannel, consultMinutes) {
  return http.post('/api/consultations/orders', { consultationId, packageCode, payChannel, consultMinutes })
}

/** 结束问诊，释放医生。 */
export function endConsult(consultationId, reason) {
  return http.post('/api/consultations/end', { consultationId, reason })
}

/** 问诊中改时长。 */
export function setConsultMinutes(consultationId, consultMinutes) {
  return http.post('/api/consultations/session', { consultationId, consultMinutes })
}

/** 把当前账号的一位就诊人绑到本轮咨询。 */
export function bindPatient(consultationId, patientId) {
  return http.post('/api/consultations/patient', { consultationId, patientId })
}
