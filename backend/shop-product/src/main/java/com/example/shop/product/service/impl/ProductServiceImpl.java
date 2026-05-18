package com.example.shop.product.service.impl;

import com.example.shop.common.entity.Product;
import com.example.shop.product.dao.ProductDao;
import com.example.shop.product.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ProductServiceImpl implements ProductService {

    @Autowired
    private ProductDao productDao;

    @Override
    public Product findById(Integer pid) {
        return productDao.findById(pid).orElse(null);
    }

    @Override
    public Product save(Product product) {
        return productDao.save(product);
    }
}
