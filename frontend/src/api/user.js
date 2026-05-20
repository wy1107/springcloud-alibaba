import request from './request'

/**
 * 用户服务API
 * 对接后端 UserController + AuthController，通过Gateway路由 /user/**
 * /user/** 是Gateway白名单路径，无需token参数
 */
export const userApi = {
  /** 用户登录（uid + telephone 认证，返回JWT token） */
  login(uid, telephone) {
    return request.post('/user/login', { uid: String(uid), telephone })
  },

  /** 根据用户ID查询用户信息 */
  findById(uid) {
    return request.get(`/user/${uid}`)
  }
}
