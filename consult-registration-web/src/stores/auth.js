import { defineStore } from 'pinia'
import { login as loginApi, sendSms } from '../api/auth'

const TOKEN_KEY = 'consult-token'
const ACCOUNT_KEY = 'consult-account'
const MOBILE_KEY = 'consult-mobile'

function read(key) {
  try {
    return localStorage.getItem(key) || ''
  } catch {
    return ''
  }
}

/**
 * 患者登录态。进咨询页之前必须有令牌。
 */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: read(TOKEN_KEY),
    accountId: read(ACCOUNT_KEY),
    mobile: read(MOBILE_KEY),
  }),
  getters: {
    signedIn: (state) => !!state.token,
  },
  actions: {
    async sendCode(mobile) {
      const result = await sendSms(mobile)
      if (result.code !== 200) {
        throw new Error(result.message || '验证码发送失败')
      }
    },
    async login(mobile, captcha) {
      const result = await loginApi(mobile, captcha)
      if (result.code !== 200 || !result.data?.token) {
        throw new Error(result.message || '登录失败')
      }
      this.token = result.data.token
      this.accountId = result.data.accountId
      this.mobile = result.data.mobile
      localStorage.setItem(TOKEN_KEY, this.token)
      localStorage.setItem(ACCOUNT_KEY, this.accountId)
      localStorage.setItem(MOBILE_KEY, this.mobile)
    },
    logout() {
      this.token = ''
      this.accountId = ''
      this.mobile = ''
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(ACCOUNT_KEY)
      localStorage.removeItem(MOBILE_KEY)
    },
  },
})
