import axios from 'axios'

export const http = axios.create({ timeout: 20000 })

http.interceptors.request.use((config) => {
  config.headers = config.headers || {}
  const token = localStorage.getItem('consult-admin-token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && body.code === 1201 && !window.location.pathname.startsWith('/login')) {
      localStorage.removeItem('consult-admin-token')
      window.location.href = '/login'
    }
    return body
  },
  (error) => {
    const message = error.response?.data?.message || error.message || '网络异常'
    return Promise.reject(new Error(message))
  },
)
