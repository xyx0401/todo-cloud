package com.example.userservice.service;

import com.example.userservice.dto.UserDTO;
import com.example.userservice.entity.User;
import com.example.userservice.entity.Role;
import com.example.userservice.entity.UserRole;
import com.example.userservice.repository.UserRepository;
import com.example.userservice.repository.RoleRepository;
import com.example.userservice.repository.UserRoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public List<UserDTO> findAll() {
        log.info("查询所有用户");
        return userRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public UserDTO findById(Long id) {
        log.info("根据ID查询用户: {}", id);
        return userRepository.findById(id)
                .map(this::convertToDTO)
                .orElseThrow(() -> {
                    log.warn("未找到用户: {}", id);
                    return new RuntimeException("User not found");
                });
    }

    public UserDTO findByUsername(String username) {
        log.info("根据用户名查询用户: {}", username);
        return userRepository.findByUsername(username)
                .map(this::convertToDTO)
                .orElseThrow(() -> {
                    log.warn("未找到用户: {}", username);
                    return new RuntimeException("User not found");
                });
    }

    @Transactional
    public UserDTO create(UserDTO userDTO) {
        log.info("创建用户: {}", userDTO.getUsername());
        if (userRepository.existsByUsername(userDTO.getUsername())) {
            log.warn("用户名已存在: {}", userDTO.getUsername());
            throw new RuntimeException("Username already exists");
        }

        User user = new User();
        user.setUsername(userDTO.getUsername());
        user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        user.setEmail(userDTO.getEmail());
        user.setPhone(userDTO.getPhone());
        user.setStatus(userDTO.getStatus() != null ? userDTO.getStatus() : 1); // 默认启用

        User savedUser = userRepository.save(user);
        
        // 为新用户自动分配默认的ROLE_USER角色
        assignDefaultRole(savedUser.getId());
        
        return convertToDTO(savedUser);
    }

    @Transactional
    public UserDTO update(Long id, UserDTO userDTO) {
        log.info("更新用户: {}", id);
        User user = userRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("未找到用户: {}", id);
                    return new RuntimeException("User not found");
                });

        // 只更新提供的字段
        if (userDTO.getUsername() != null && !userDTO.getUsername().trim().isEmpty()) {
            // 检查用户名是否已被其他用户使用
            if (!user.getUsername().equals(userDTO.getUsername()) && 
                userRepository.existsByUsername(userDTO.getUsername())) {
                throw new RuntimeException("Username already exists");
            }
            user.setUsername(userDTO.getUsername());
        }
        
        if (userDTO.getPassword() != null && !userDTO.getPassword().trim().isEmpty()) {
            user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        }
        
        if (userDTO.getEmail() != null) {
            user.setEmail(userDTO.getEmail().trim().isEmpty() ? null : userDTO.getEmail());
        }
        
        if (userDTO.getPhone() != null) {
            user.setPhone(userDTO.getPhone().trim().isEmpty() ? null : userDTO.getPhone());
        }
        
        if (userDTO.getStatus() != null) {
            user.setStatus(userDTO.getStatus());
        }

        return convertToDTO(userRepository.save(user));
    }

    @Transactional
    public void delete(Long id) {
        log.info("删除用户: {}", id);
        
        // 检查用户是否存在
        User user = userRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("尝试删除不存在的用户: {}", id);
                    return new RuntimeException("User not found");
                });
        
        // 先删除用户的所有角色关联
        List<UserRole> userRoles = userRoleRepository.findByUserId(id);
        if (!userRoles.isEmpty()) {
            userRoleRepository.deleteAll(userRoles);
            log.info("删除用户 {} 的 {} 个角色关联", id, userRoles.size());
        }
        
        // 然后删除用户
        userRepository.deleteById(id);
        log.info("用户删除成功: {} ({})", user.getUsername(), id);
    }

    /**
     * 检查用户名是否存在
     */
    public boolean existsByUsername(String username) {
        log.info("检查用户名是否存在: {}", username);
        return userRepository.existsByUsername(username);
    }

    private UserDTO convertToDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setStatus(user.getStatus());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setUpdatedAt(user.getUpdatedAt());
        // 不返回密码给前端
        return dto;
    }

    /**
     * 获取用户角色
     */
    public String[] getUserRoles(Long userId) {
        log.info("获取用户角色: {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        List<UserRole> userRoles = userRoleRepository.findByUserId(userId);
        return userRoles.stream()
                .map(ur -> ur.getRole().getName())
                .toArray(String[]::new);
    }

    /**
     * 为用户添加管理员角色
     */
    @Transactional
    public void addAdminRole(Long userId) {
        log.info("为用户添加管理员角色: {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        Role adminRole = roleRepository.findByName("ROLE_ADMIN")
                .orElseThrow(() -> new RuntimeException("Admin role not found"));
        
        // 检查是否已有管理员角色
        UserRole existingUserRole = userRoleRepository.findByUserIdAndRoleName(userId, "ROLE_ADMIN");
        if (existingUserRole == null) {
            UserRole userRole = new UserRole(user, adminRole);
            userRoleRepository.save(userRole);
            log.info("管理员角色添加成功: {}", userId);
        } else {
            log.info("用户已具有管理员角色: {}", userId);
        }
    }

    /**
     * 移除用户的管理员角色
     */
    @Transactional
    public void removeAdminRole(Long userId) {
        log.info("移除用户管理员角色: {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        Role adminRole = roleRepository.findByName("ROLE_ADMIN")
                .orElseThrow(() -> new RuntimeException("Admin role not found"));
        
        UserRole existingUserRole = userRoleRepository.findByUserIdAndRoleName(userId, "ROLE_ADMIN");
        if (existingUserRole != null) {
            userRoleRepository.delete(existingUserRole);
            log.info("管理员角色移除成功: {}", userId);
        } else {
            log.info("用户没有管理员角色: {}", userId);
        }
    }

    /**
     * 为新用户分配默认角色（ROLE_USER）
     */
    @Transactional
    public void assignDefaultRole(Long userId) {
        try {
            log.info("为新用户分配默认角色: {}", userId);
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            Role userRole = roleRepository.findByName("ROLE_USER")
                    .orElseThrow(() -> new RuntimeException("User role not found"));
            
            // 检查是否已有用户角色
            UserRole existingUserRole = userRoleRepository.findByUserIdAndRoleName(userId, "ROLE_USER");
            if (existingUserRole == null) {
                UserRole newUserRole = new UserRole(user, userRole);
                userRoleRepository.save(newUserRole);
                log.info("默认用户角色分配成功: {}", userId);
            } else {
                log.info("用户已具有用户角色: {}", userId);
            }
        } catch (Exception e) {
            log.error("分配默认角色失败，用户ID: {}, 错误: {}", userId, e.getMessage());
            // 不抛出异常，避免影响用户创建
        }
    }

    /**
     * 修复没有角色的用户，为他们分配默认的ROLE_USER角色
     */
    @Transactional
    public void fixUsersWithoutRoles() {
        log.info("开始修复没有角色的用户");
        List<User> allUsers = userRepository.findAll();
        int fixedCount = 0;
        
        for (User user : allUsers) {
            List<UserRole> userRoles = userRoleRepository.findByUserId(user.getId());
            if (userRoles.isEmpty()) {
                log.info("发现没有角色的用户: {} (ID: {})", user.getUsername(), user.getId());
                assignDefaultRole(user.getId());
                fixedCount++;
            }
        }
        
        log.info("修复完成，共为{}个用户分配了默认角色", fixedCount);
    }
} 