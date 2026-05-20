package com.example.shop.user.dao;

import com.example.shop.common.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserDao extends JpaRepository<User, Integer> {

    /**
     * 根据手机号查询用户（登录验证用）
     */
    Optional<User> findByTelephone(String telephone);
}
