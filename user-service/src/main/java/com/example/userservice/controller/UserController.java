package com.example.userservice.controller;

import com.example.userservice.dto.UserDTO;
import com.example.userservice.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// import javax.validation.Valid; // 移除validation依赖
import java.util.List;

/**
 * 用户管理REST API控制器
 * 提供完整的用户增删改查功能
 */
@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {
    
    private static final Logger log = LoggerFactory.getLogger(UserController.class);
    
    @Autowired
    private UserService userService;

    /**
     * 获取所有用户列表
     */
    @GetMapping
    public ResponseEntity<List<UserDTO>> findAll() {
        try {
            log.info("获取所有用户列表");
            List<UserDTO> users = userService.findAll();
            log.info("成功获取{}个用户", users.size());
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            log.error("获取用户列表失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
    }

    /**
     * 根据ID获取用户信息
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserDTO> findById(@PathVariable Long id) {
        try {
            log.info("根据ID获取用户信息: {}", id);
            UserDTO user = userService.findById(id);
            return ResponseEntity.ok(user);
        } catch (RuntimeException e) {
            log.warn("用户不存在: {}", id);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("获取用户信息失败: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
    }

    /**
     * 根据用户名获取用户信息
     */
    @GetMapping("/username/{username}")
    public ResponseEntity<UserDTO> findByUsername(@PathVariable String username) {
        try {
            log.info("根据用户名获取用户信息: {}", username);
            UserDTO user = userService.findByUsername(username);
            return ResponseEntity.ok(user);
        } catch (RuntimeException e) {
            log.warn("用户不存在: {}", username);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("获取用户信息失败: {}", username, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 检查用户名是否存在
     */
    @GetMapping("/check/{username}")
    public ResponseEntity<Boolean> checkUsernameExists(@PathVariable String username) {
        try {
            log.info("检查用户名是否存在: {}", username);
            boolean exists = userService.existsByUsername(username);
            return ResponseEntity.ok(exists);
        } catch (Exception e) {
            log.error("检查用户名失败: {}", username, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 创建新用户
     */
    @PostMapping
    public ResponseEntity<UserDTO> create(@RequestBody UserDTO userDTO) {
        try {
            log.info("创建新用户: {}", userDTO.getUsername());
            
            // 验证必填字段
            if (userDTO.getUsername() == null || userDTO.getUsername().trim().isEmpty()) {
                log.warn("用户名不能为空");
                return ResponseEntity.badRequest().build();
            }
            if (userDTO.getPassword() == null || userDTO.getPassword().trim().isEmpty()) {
                log.warn("密码不能为空");
                return ResponseEntity.badRequest().build();
            }
            
            UserDTO createdUser = userService.create(userDTO);
            log.info("用户创建成功: {}", createdUser.getUsername());
            return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
            
        } catch (RuntimeException e) {
            if (e.getMessage().contains("already exists")) {
                log.warn("用户名已存在: {}", userDTO.getUsername());
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }
            log.error("创建用户失败", e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("创建用户失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 更新用户信息
     */
    @PutMapping("/{id}")
    public ResponseEntity<UserDTO> update(@PathVariable Long id, @RequestBody UserDTO userDTO) {
        try {
            log.info("更新用户信息: {}", id);
            
            // 验证用户名不为空（如果提供了的话）
            if (userDTO.getUsername() != null && userDTO.getUsername().trim().isEmpty()) {
                log.warn("用户名不能为空");
                return ResponseEntity.badRequest().build();
            }
            
            UserDTO updatedUser = userService.update(id, userDTO);
            log.info("用户更新成功: {}", updatedUser.getUsername());
            return ResponseEntity.ok(updatedUser);
            
        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found")) {
                log.warn("用户不存在: {}", id);
                return ResponseEntity.notFound().build();
            }
            if (e.getMessage().contains("already exists")) {
                log.warn("用户名已存在: {}", userDTO.getUsername());
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }
            log.error("更新用户失败", e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("更新用户失败: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        try {
            log.info("删除用户: {}", id);
        userService.delete(id);
            log.info("用户删除成功: {}", id);
        return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            if (e.getMessage().contains("not found")) {
                log.warn("用户不存在: {}", id);
                return ResponseEntity.notFound().build();
            }
            log.error("删除用户失败", e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("删除用户失败: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 批量删除用户
     */
    @DeleteMapping("/batch")
    public ResponseEntity<String> batchDelete(@RequestBody List<Long> userIds) {
        try {
            log.info("批量删除用户，数量: {}", userIds.size());
            int deletedCount = 0;
            for (Long id : userIds) {
                try {
                    userService.delete(id);
                    deletedCount++;
                } catch (Exception e) {
                    log.warn("删除用户失败: {}", id, e);
                }
            }
            log.info("批量删除完成，成功删除{}个用户", deletedCount);
            return ResponseEntity.ok("成功删除" + deletedCount + "个用户");
        } catch (Exception e) {
            log.error("批量删除用户失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("批量删除失败");
        }
    }

    /**
     * 获取用户统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<Object> getUserStats() {
        try {
            List<UserDTO> users = userService.findAll();
            long totalCount = users.size();
            
            return ResponseEntity.ok(new Object() {
                public final long total = totalCount;
                public final String message = "共有" + totalCount + "个用户";
            });
        } catch (Exception e) {
            log.error("获取用户统计失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 获取用户角色
     */
    @GetMapping("/{id}/roles")
    public ResponseEntity<String[]> getUserRoles(@PathVariable Long id) {
        try {
            log.info("获取用户角色: {}", id);
            String[] roles = userService.getUserRoles(id);
            return ResponseEntity.ok(roles);
        } catch (RuntimeException e) {
            log.warn("用户不存在: {}", id);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("获取用户角色失败: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 为用户添加管理员角色
     */
    @PostMapping("/{id}/roles/admin")
    public ResponseEntity<String> addAdminRole(@PathVariable Long id) {
        try {
            log.info("为用户添加管理员角色: {}", id);
            userService.addAdminRole(id);
            return ResponseEntity.ok("管理员角色添加成功");
        } catch (RuntimeException e) {
            log.warn("用户不存在: {}", id);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("添加管理员角色失败: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 移除用户的管理员角色
     */
    @DeleteMapping("/{id}/roles/admin")
    public ResponseEntity<String> removeAdminRole(@PathVariable Long id) {
        try {
            log.info("移除用户管理员角色: {}", id);
            userService.removeAdminRole(id);
            return ResponseEntity.ok("管理员角色移除成功");
        } catch (RuntimeException e) {
            log.warn("用户不存在: {}", id);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("移除管理员角色失败: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 修复没有角色的用户
     */
    @PostMapping("/fix-roles")
    public ResponseEntity<String> fixUsersWithoutRoles() {
        try {
            log.info("手动触发修复没有角色的用户");
            userService.fixUsersWithoutRoles();
            return ResponseEntity.ok("用户角色修复完成");
        } catch (Exception e) {
            log.error("修复用户角色失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("修复失败");
        }
    }
} 