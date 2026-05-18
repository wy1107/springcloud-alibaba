package com.example.shop.product.service;

import com.example.shop.common.entity.Product;

public interface ProductService {

    Product findById(Integer pid);

    Product save(Product product);
}
