import request from './request'

/**
 * 订单服务API
 * 对接后端 OrderController，通过Gateway路由 /order/**
 */
export const orderApi = {
  /** 创建订单 */
  create(pid, uid) {
    return request.get('/order/create', { params: { pid, uid } })
  },

  /** 负载均衡验证接口 */
  loadBalanceTest() {
    return request.get('/order/lb-test')
  }
}
