package com.example.shop.user.controller;

import com.example.shop.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 认证控制器
 *
 * 提供用户登录接口，验证uid+手机号后签发JWT token
 * 路径 /user/login 已在Gateway白名单中，无需token即可访问
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class AuthController {

    @Autowired
    private UserService userService;

    /**
     * 用户登录
     *
     * 请求体：{"uid": 1, "telephone": "13800138001"}
     * 响应体：{"token": "xxx", "uid": 1, "username": "张三", "telephone": "13800138001"}
     *
     * @param loginRequest 登录请求参数
     * @return 登录结果（含token和用户信息）
     */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> loginRequest) {
        String uidStr = loginRequest.get("uid");
        String telephone = loginRequest.get("telephone");

        if (uidStr == null || uidStr.trim().isEmpty()) {
            throw new RuntimeException("用户ID不能为空");
        }
        if (telephone == null || telephone.trim().isEmpty()) {
            throw new RuntimeException("手机号不能为空");
        }

        Integer uid;
        try {
            uid = Integer.parseInt(uidStr);
        } catch (NumberFormatException e) {
            throw new RuntimeException("用户ID格式不正确");
        }

        return userService.login(uid, telephone);
    }
}
