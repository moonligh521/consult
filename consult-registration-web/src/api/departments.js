import { http } from './http'

/** 患者选科树。只含开通一级和二级。 */
export function listPatientDepartments(age) {
  const query = Number.isInteger(age) ? `?age=${age}` : ''
  return http.get(`/api/departments${query}`)
}
