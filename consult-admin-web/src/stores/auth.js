import { defineStore } from 'pinia'
import { staffLogin, staffMe } from '../api/admin'

const TOKEN_KEY = 'consult-admin-token'
const DOCTOR_KEY = 'consult-admin-doctor'

export const useAuthStore = defineStore('admin-auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    name: '',
    username: '',
    doctorId: localStorage.getItem(DOCTOR_KEY) || '',
  }),
  getters: {
    signedIn: (state) => !!state.token,
  },
  actions: {
    async login(username, password) {
      const result = await staffLogin(username, password)
      if (result.code !== 200 || !result.data?.token) {
        throw new Error(result.message || '登录失败')
      }
      this.token = result.data.token
      this.name = result.data.name
      this.username = result.data.username
      this.doctorId = result.data.doctorId || ''
      localStorage.setItem(TOKEN_KEY, this.token)
      if (this.doctorId) {
        localStorage.setItem(DOCTOR_KEY, this.doctorId)
      } else {
        localStorage.removeItem(DOCTOR_KEY)
      }
    },
    async loadMe() {
      if (!this.token) {
        return
      }
      const result = await staffMe()
      if (result.code === 200 && result.data) {
        this.name = result.data.name
        this.username = result.data.username
        this.doctorId = result.data.doctorId || ''
        if (this.doctorId) {
          localStorage.setItem(DOCTOR_KEY, this.doctorId)
        } else {
          localStorage.removeItem(DOCTOR_KEY)
        }
      }
    },
    logout() {
      this.token = ''
      this.name = ''
      this.username = ''
      this.doctorId = ''
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(DOCTOR_KEY)
    },
  },
})
