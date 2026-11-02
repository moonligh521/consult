import { defineStore } from 'pinia'
import { staffLogin, staffMe } from '../api/admin'

const TOKEN_KEY = 'consult-admin-token'

export const useAuthStore = defineStore('admin-auth', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    name: '',
    username: '',
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
      localStorage.setItem(TOKEN_KEY, this.token)
    },
    async loadMe() {
      if (!this.token) {
        return
      }
      const result = await staffMe()
      if (result.code === 200 && result.data) {
        this.name = result.data.name
        this.username = result.data.username
      }
    },
    logout() {
      this.token = ''
      this.name = ''
      this.username = ''
      localStorage.removeItem(TOKEN_KEY)
    },
  },
})
