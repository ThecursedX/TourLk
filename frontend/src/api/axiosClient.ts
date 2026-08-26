import axios from 'axios'
import { useAuthStore } from '../auth/authStore'

const axiosClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
})

axiosClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

axiosClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      useAuthStore.getState().logout()
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    }
    return Promise.reject(error)
  },
)

export default axiosClient
