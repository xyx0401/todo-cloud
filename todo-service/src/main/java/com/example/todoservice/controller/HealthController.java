package com.example.todoservice.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查和系统信息控制器
 */
@RestController
@RequestMapping("/api")
public class HealthController {
    
    private static final Logger log = LoggerFactory.getLogger(HealthController.class);
    
    @Autowired
    private Environment environment;
    
    @Autowired
    private RestTemplate restTemplate;
    
    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "todo-service");
        health.put("timestamp", LocalDateTime.now());
        health.put("port", environment.getProperty("server.port", "8081"));
        
        return ResponseEntity.ok(health);
    }
    
    /**
     * 服务信息
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> info() {
        Map<String, Object> info = new HashMap<>();
        info.put("service", "todo-service");
        info.put("version", "1.0.0");
        info.put("description", "待办事项微服务");
        info.put("port", environment.getProperty("server.port", "8081"));
        info.put("profile", environment.getActiveProfiles());
        
        return ResponseEntity.ok(info);
    }
    
    /**
     * 连接测试 - 测试与其他微服务的连接
     */
    @GetMapping("/connectivity")
    public ResponseEntity<Map<String, Object>> testConnectivity() {
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", LocalDateTime.now());
        
        // 测试用户服务连接
        try {
            ResponseEntity<Map> userServiceResponse = restTemplate.getForEntity(
                "http://user-service/api/users/stats", Map.class
            );
            result.put("user-service", Map.of(
                "status", "UP",
                "response", userServiceResponse.getBody()
            ));
        } catch (Exception e) {
            result.put("user-service", Map.of(
                "status", "DOWN",
                "error", e.getMessage()
            ));
        }
        
        // 测试认证服务连接（如果有相关接口）
        try {
            // 这里可以添加对auth-service的测试
            result.put("auth-service", Map.of("status", "NOT_TESTED"));
        } catch (Exception e) {
            result.put("auth-service", Map.of(
                "status", "DOWN",
                "error", e.getMessage()
            ));
        }
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * 会话信息
     */
    @GetMapping("/session")
    public ResponseEntity<Map<String, Object>> sessionInfo(HttpSession session) {
        Map<String, Object> sessionInfo = new HashMap<>();
        
        sessionInfo.put("sessionId", session.getId());
        sessionInfo.put("creationTime", session.getCreationTime());
        sessionInfo.put("lastAccessedTime", session.getLastAccessedTime());
        sessionInfo.put("maxInactiveInterval", session.getMaxInactiveInterval());
        
        // 用户信息
        String username = (String) session.getAttribute("username");
        Long userId = (Long) session.getAttribute("userId");
        
        if (username != null) {
            sessionInfo.put("user", Map.of(
                "username", username,
                "userId", userId != null ? userId : "unknown",
                "isAdmin", "admin".equals(username)
            ));
        } else {
            sessionInfo.put("user", null);
        }
        
        return ResponseEntity.ok(sessionInfo);
    }
    
    /**
     * 数据库连接测试
     */
    @GetMapping("/db-test")
    public ResponseEntity<Map<String, Object>> databaseTest() {
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", LocalDateTime.now());
        
        try {
            // 测试tododb连接
            try {
                ResponseEntity<Object> todoStats = restTemplate.getForEntity(
                    "http://localhost:8081/api/todos/stats", Object.class
                );
                result.put("tododb", Map.of(
                    "status", "UP",
                    "message", "Todo数据库连接正常",
                    "stats", todoStats.getBody()
                ));
            } catch (Exception e) {
                result.put("tododb", Map.of(
                    "status", "DOWN",
                    "error", e.getMessage()
                ));
            }
            
            // 测试userdb连接
            try {
                ResponseEntity<Object> userStats = restTemplate.getForEntity(
                    "http://localhost:8082/api/users/stats", Object.class
                );
                result.put("userdb", Map.of(
                    "status", "UP", 
                    "message", "用户数据库连接正常",
                    "stats", userStats.getBody()
                ));
            } catch (Exception e) {
                result.put("userdb", Map.of(
                    "status", "DOWN",
                    "error", e.getMessage()
                ));
            }
            
        } catch (Exception e) {
            result.put("database", Map.of(
                "status", "DOWN",
                "error", e.getMessage()
            ));
        }
        
        return ResponseEntity.ok(result);
    }
} 