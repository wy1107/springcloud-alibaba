package com.example.shop.product.controller;

import com.example.shop.common.entity.Product;
import com.example.shop.product.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/product")
public class ProductController {

    @Autowired
    private ProductService productService;

    @GetMapping("/{pid}")
    public Product findById(@PathVariable Integer pid) {
        return productService.findById(pid);
    }
}
