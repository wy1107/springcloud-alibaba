import request from './request'

/**
 * 商品服务API
 * 对接后端 ProductController，通过Gateway路由 /product/**
 */
export const productApi = {
  /** 根据商品ID查询商品信息 */
  findById(pid) {
    return request.get(`/product/${pid}`)
  },

  /** 负载均衡验证接口 */
  getPort() {
    return request.get('/product/port')
  },

  /** 秒杀接口 */
  seckill(productId) {
    return request.get(`/product/seckill/${productId}`)
  }
}
