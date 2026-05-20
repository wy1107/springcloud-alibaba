import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import router from '@/router'

/**
 * Axios请求封装
 * 统一处理：JWT token注入、401自动跳转登录、错误提示
 */
const request = axios.create({
  baseURL: '',
  timeout: 10000
})

// 请求拦截器：注入JWT token
request.interceptors.request.use(
  (config) => {
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers.Authorization = `Bearer ${userStore.token}`
    }
    // Gateway AuthGlobalFilter需要token参数（非白名单路径）
    if (userStore.token && config.url) {
      const path = config.url
      // /user/** 是白名单，不需要token参数；其他路径需要
      if (!path.startsWith('/user/')) {
        config.params = config.params || {}
        config.params.token = userStore.token
      }
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器：统一错误处理
request.interceptors.response.use(
  (response) => {
    return response.data
  },
  (error) => {
    const status = error.response?.status
    const data = error.response?.data

    if (status === 401) {
      const msg = data?.msg || '认证失败'
      ElMessageBox.confirm(
        `${msg}，请重新登录`,
        '登录过期',
        { confirmButtonText: '重新登录', cancelButtonText: '取消', type: 'warning' }
      ).then(() => {
        const userStore = useUserStore()
        userStore.logout()
        router.push('/login')
      })
    } else if (status === 429) {
      ElMessage.warning(data?.msg || '请求过于频繁，请稍后重试')
    } else if (status === 404) {
      ElMessage.error('请求的资源不存在')
    } else if (status >= 500) {
      ElMessage.error('服务器内部错误，请稍后重试')
    } else {
      ElMessage.error(data?.msg || error.message || '请求失败')
    }

    return Promise.reject(error)
  }
)

export default request
