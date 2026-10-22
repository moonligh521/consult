import axios from 'axios'

/** 走 Vite 代理，开发时不用写后端地址。 */
export const http = axios.create({
  timeout: 20000,
})

http.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const message = error.response?.data?.message || error.message || '网络异常'
    return Promise.reject(new Error(message))
  },
)
