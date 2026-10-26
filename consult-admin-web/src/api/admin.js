import { http } from './http'

export function staffLogin(username, password) {
  return http.post('/api/admin/login', { username, password })
}

export function staffMe() {
  return http.get('/api/admin/me')
}

export function listAccounts() {
  return http.get('/api/admin/accounts')
}

export function listPatients(accountId) {
  return http.get('/api/admin/patients', { params: accountId ? { accountId } : {} })
}

export function listConsultations() {
  return http.get('/api/admin/consultations')
}

export async function listDepartments() {
  const result = await http.get('/api/admin/departments')
  if (result?.code === 200 && Array.isArray(result.data)) {
    result.data = [...result.data].sort((a, b) => a.code - b.code)
  }
  return result
}

export function listDoctors() {
  return http.get('/api/admin/doctors')
}

export function createDoctor(payload) {
  return http.post('/api/admin/doctors', payload)
}

export function updateDoctor(id, payload) {
  return http.patch(`/api/admin/doctors/${id}`, payload)
}

export function listSchedules(params) {
  return http.get('/api/admin/schedules', { params })
}

export function createSchedule(payload) {
  return http.post('/api/admin/schedules', payload)
}

export function stopSchedule(id, stopped) {
  return http.patch(`/api/admin/schedules/${id}/stop`, null, { params: { stopped } })
}

export function deleteSchedule(id) {
  return http.delete(`/api/admin/schedules/${id}`)
}
