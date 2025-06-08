package com.example.userservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .authorizeRequests()
                .antMatchers("/login", "/api/auth/**", "/api/users/**").permitAll() // 放行登录、注册、用户API
                .anyRequest().authenticated()
            .and()
            .formLogin()
                .loginPage("/login") // 如有自定义登录页，否则可去掉
                .permitAll();
        return http.build();
    }
} 