package com.example.shop.user.service.impl;

import com.example.shop.common.entity.User;
import com.example.shop.user.dao.UserDao;
import com.example.shop.user.service.UserService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

    /**
     * JWT签名密钥，必须与Gateway的JwtAuthenticationGlobalFilter保持一致
     */
    private static final String SECRET_KEY = "shop-gateway-secret-key-256bit";

    /**
     * JWT过期时间：1小时
     */
    private static final long EXPIRATION_MS = 3600000L;

    /**
     * 签名Key对象（HMAC-SHA256）
     */
    private static final Key SIGNING_KEY = new SecretKeySpec(
            SECRET_KEY.getBytes(StandardCharsets.UTF_8),
            SignatureAlgorithm.HS256.getJcaName()
    );

    @Autowired
    private UserDao userDao;

    @Override
    public User findById(Integer uid) {
        return userDao.findById(uid).orElse(null);
    }

    @Override
    public Map<String, Object> login(Integer uid, String telephone) {
        // 1. 根据uid查询用户
        Optional<User> userOpt = userDao.findById(uid);
        if (!userOpt.isPresent()) {
            throw new RuntimeException("用户不存在");
        }

        User user = userOpt.get();

        // 2. 验证手机号是否匹配
        if (!telephone.equals(user.getTelephone())) {
            throw new RuntimeException("手机号不匹配");
        }

        // 3. 签发JWT token
        String token = Jwts.builder()
                .setSubject(String.valueOf(user.getUid()))
                .claim("username", user.getUsername())
                .claim("authorities", "ROLE_USER")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                .signWith(SignatureAlgorithm.HS256, SIGNING_KEY)
                .compact();

        log.info("[登录成功] uid: {}, username: {}", user.getUid(), user.getUsername());

        // 4. 返回token和用户信息
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("uid", user.getUid());
        result.put("username", user.getUsername());
        result.put("telephone", user.getTelephone());
        return result;
    }
}
