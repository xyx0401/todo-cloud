package com.example.userservice.service;

import com.example.userservice.entity.User;
import com.example.userservice.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {
    private static final Logger log = LoggerFactory.getLogger(CustomUserDetailsService.class);
    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.info("尝试登录用户名: {}", username);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("未找到用户: {}", username);
                    return new UsernameNotFoundException("User not found");
                });
        List<GrantedAuthority> authorities = new ArrayList<>();
        // 简单处理，默认所有用户ROLE_USER，实际可查user_roles表
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        boolean enabled = user.getStatus() != null && user.getStatus() == 1;
        if (!enabled) {
            log.warn("用户被禁用: {}", username);
        }
        log.info("用户{}登录成功，enabled={}，id={}", username, enabled, user.getId());
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                enabled,
                true, true, true,
                authorities
        );
    }
} 