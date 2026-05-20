import { defineStore } from 'pinia'
import { userApi } from '@/api/user'

/**
 * 用户状态管理
 * 管理JWT token、用户信息、登录/登出逻辑
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    userId: localStorage.getItem('userId') || '',
    username: localStorage.getItem('username') || '',
    telephone: localStorage.getItem('telephone') || ''
  }),

  getters: {
    isLoggedIn: (state) => !!state.token
  },

  actions: {
    /**
     * 用户登录
     * 调用后端 /user/login 接口，传入 uid + telephone 获取JWT token
     */
    async login(uid, telephone) {
      const data = await userApi.login(uid, telephone)
      if (data && data.token) {
        this.token = data.token
        this.userId = String(data.uid)
        this.username = data.username
        this.telephone = data.telephone || ''
        localStorage.setItem('token', data.token)
        localStorage.setItem('userId', String(data.uid))
        localStorage.setItem('username', data.username)
        localStorage.setItem('telephone', data.telephone || '')
        return data
      }
      throw new Error('登录失败，服务器未返回有效凭证')
    },

    /** 登出 */
    logout() {
      this.token = ''
      this.userId = ''
      this.username = ''
      this.telephone = ''
      localStorage.removeItem('token')
      localStorage.removeItem('userId')
      localStorage.removeItem('username')
      localStorage.removeItem('telephone')
    }
  }
})
