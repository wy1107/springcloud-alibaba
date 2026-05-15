package com.example.shop.product.handler;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.example.shop.common.entity.Product;
import lombok.extern.slf4j.Slf4j;

/**
 * 商品服务Sentinel限流/熔断处理类（blockHandler）
 * 处理BlockException及其子异常：FlowException、DegradeException、
 * ParamFlowException、AuthorityException、SystemBlockException
 *
 * 注意：作为blockHandlerClass外部类使用时，方法必须为static，
 * 且方法签名需与原方法参数一致，最后追加BlockException参数
 */
@Slf4j
public class ProductBlockHandler {

    /**
     * 商品查询接口的限流/熔断处理
     * @param pid 商品ID（与原方法参数保持一致）
     * @param ex Sentinel阻断异常（包含具体阻断原因）
     * @return 降级后的商品对象
     */
    public static Product findByIdBlockHandler(Integer pid, BlockException ex) {
        Product product = new Product();
        product.setPid(pid);
        product.setPname("商品服务限流/熔断降级-触发Sentinel保护");
        product.setPprice(0.0);
        product.setStock(0);
        log.info("限流后的商品值：{}",product);
        return product;
    }

    /**
     * 秒杀接口的限流/熔断处理
     * @param productId 商品ID
     * @param ex Sentinel阻断异常
     * @return 限流提示信息
     */
    public static String seckillBlockHandler(Integer productId, BlockException ex) {
        return "{\"code\":429,\"msg\":\"抢购火爆，请稍后再试\"}";
    }
}
