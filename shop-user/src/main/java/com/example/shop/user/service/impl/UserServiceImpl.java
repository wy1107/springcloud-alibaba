package com.example.shop.user.service.impl;

import com.example.shop.common.entity.User;
import com.example.shop.user.dao.UserDao;
import com.example.shop.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserDao userDao;

    @Override
    public User findById(Integer uid) {
        return userDao.findById(uid).orElse(null);
    }
}
