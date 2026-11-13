import { http } from './http'

/** 当前账号下的就诊人列表。 */
export function listPatients() {
  return http.get('/api/patients')
}

/** 给当前账号新增一位就诊人。 */
export function createPatient(payload) {
  return http.post('/api/patients', payload)
}
