package com.example.shop.user.service;

import com.example.shop.common.entity.User;

import java.util.Map;

public interface UserService {

    User findById(Integer uid);

    /**
     * 用户登录认证
     * @param uid 用户ID
     * @param telephone 手机号
     * @return 包含JWT token和用户信息的Map
     */
    Map<String, Object> login(Integer uid, String telephone);
}
