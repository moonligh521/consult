import { http } from './http'

export function sendSms(mobile) {
  return http.post('/api/auth/sms', { mobile })
}

export function login(mobile, captcha) {
  return http.post('/api/auth/login', { mobile, captcha })
}
