package com.example.authservice.service;

import com.example.authservice.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    @Autowired
    private JwtUtil jwtUtil;

    public String generateToken(String username) {
        log.info("生成token: {}", username);
        return jwtUtil.generateToken(username);
    }

    public boolean validateToken(String token) {
        try {
            boolean valid = jwtUtil.validateToken(token);
            log.info("校验token: {}，结果: {}", token, valid);
            return valid;
        } catch (Exception e) {
            log.error("token校验异常", e);
            return false;
        }
    }

    public String getUsernameFromToken(String token) {
        try {
            String username = jwtUtil.extractUsername(token);
            log.info("从token提取用户名: {}", username);
            return username;
        } catch (Exception e) {
            log.error("token解析异常", e);
            throw e;
        }
    }
} 