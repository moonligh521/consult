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

/** 当前账号一年内咨询记录。 */
export function listConsultations(page = 1, size = 50) {
  return http.get('/api/consultations', { params: { page, size } })
}

/** 打开一条咨询记录。 */
export function getConsultation(consultationId) {
  return http.get(`/api/consultations/${consultationId}`)
}

/** 等派医生。服务端最多挂 10 秒，JWT 走 Authorization。 */
export function waitAssignment(consultationId, waitMs = 10000) {
  return http.get(`/api/consultations/${consultationId}/assignment`, {
    params: { waitMs },
    timeout: waitMs + 8000,
  })
}

/** 把诊室画面存回会话。 */
export function saveTranscript(consultationId, title, transcript) {
  return http.put('/api/consultations/transcript', { consultationId, title, transcript })
}

/** 支付后未派到医生时再派一次。 */
export function reassignDoctor(consultationId) {
  return http.post('/api/consultations/reassign', { consultationId })
}
