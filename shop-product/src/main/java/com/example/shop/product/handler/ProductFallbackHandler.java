package com.example.shop.product.handler;

import com.example.shop.common.entity.Product;

/**
 * 商品服务Sentinel降级处理类（fallback）
 * 处理业务运行时异常（非BlockException），如NullPointerException、
 * RuntimeException等，为服务提供托底方案
 *
 * 注意：作为fallbackClass外部类使用时，方法必须为static，
 * 方法签名与原方法参数一致，可追加Throwable参数获取异常详情
 */
public class ProductFallbackHandler {

    /**
     * 商品查询接口的业务异常降级处理
     * @param pid 商品ID
     * @param throwable 业务异常（可获取具体异常信息便于排查）
     * @return 兜底商品对象
     */
    public static Product findByIdFallback(Integer pid, Throwable throwable) {
        Product product = new Product();
        product.setPid(pid);
        product.setPname("商品服务异常降级-触发Fallback保护，原因: " + throwable.getMessage());
        product.setPprice(0.0);
        product.setStock(0);
        return product;
    }

    /**
     * 秒杀接口的业务异常降级处理
     * @param productId 商品ID
     * @param throwable 业务异常
     * @return 降级提示信息
     */
    public static String seckillFallback(Integer productId, Throwable throwable) {
        return "{\"code\":500,\"msg\":\"秒杀服务异常，请稍后重试，原因: " + throwable.getMessage() + "\"}";
    }
}
