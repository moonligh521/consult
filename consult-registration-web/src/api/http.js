import axios from 'axios'

/** 走 Vite 代理，开发时不用写后端地址。 */
export const http = axios.create({
  // 覆盖安全模型与主诉模型的独立超时/无效输出重试，浏览器不能提前中断完整链路。
  timeout: 115000,
})

http.interceptors.request.use((config) => {
  config.headers = config.headers || {}
  const token = localStorage.getItem('consult-token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && body.code === 1201 && !window.location.pathname.startsWith('/login')) {
      localStorage.removeItem('consult-token')
      localStorage.removeItem('consult-account')
      localStorage.removeItem('consult-mobile')
      window.location.href = '/login'
    }
    return body
  },
  (error) => {
    const body = error.response?.data
    if (body && body.code === 1201 && !window.location.pathname.startsWith('/login')) {
      localStorage.removeItem('consult-token')
      window.location.href = '/login'
    }
    const message = body?.message || error.message || '网络异常'
    return Promise.reject(new Error(message))
  },
)
