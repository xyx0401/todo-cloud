package com.example.todoservice.controller;

import com.example.todoservice.dto.UserDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpSession;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin")
public class UserManagementController {

    private static final Logger log = LoggerFactory.getLogger(UserManagementController.class);
    
    @Autowired
    private RestTemplate restTemplate;
    
    private static final String USER_SERVICE_URL = "http://localhost:8082/api/users";

    /**
     * 检查管理员权限
     */
    private boolean isAdmin(HttpSession session) {
        String username = (String) session.getAttribute("username");
        return "admin".equals(username);
    }

    /**
     * 更新用户角色
     */
    private void updateUserRole(Long userId, boolean isAdmin) {
        try {
            String roleUrl = "http://localhost:8082/api/users/" + userId + "/roles";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            if (isAdmin) {
                // 设置为管理员角色
                restTemplate.exchange(roleUrl + "/admin", HttpMethod.POST, 
                    new HttpEntity<>(headers), String.class);
                log.info("用户 {} 设置为管理员", userId);
            } else {
                // 移除管理员角色，设置为普通用户
                restTemplate.exchange(roleUrl + "/admin", HttpMethod.DELETE, 
                    new HttpEntity<>(headers), String.class);
                log.info("用户 {} 移除管理员权限", userId);
            }
        } catch (Exception e) {
            log.warn("更新用户角色失败，用户ID: {}, 错误: {}", userId, e.getMessage());
        }
    }

    /**
     * 检查用户是否为管理员
     */
    private boolean checkUserIsAdmin(Long userId) {
        try {
            String roleUrl = "http://localhost:8082/api/users/" + userId + "/roles";
            ResponseEntity<String[]> response = restTemplate.getForEntity(roleUrl, String[].class);
            String[] roles = response.getBody();
            if (roles != null) {
                for (String role : roles) {
                    if ("ROLE_ADMIN".equals(role)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("检查用户角色失败，用户ID: {}", userId);
        }
        return false;
    }

    /**
     * 创建模拟用户数据用于测试（当user-service不可用时）
     */
    private List<UserDTO> createMockUsers() {
        List<UserDTO> mockUsers = new java.util.ArrayList<>();
        
        UserDTO admin = new UserDTO();
        admin.setId(1L);
        admin.setUsername("admin");
        admin.setEmail("admin@example.com");
        admin.setPhone("");
        admin.setStatus(1);
        admin.setCreatedAt(java.time.LocalDateTime.of(2024, 1, 1, 12, 0));
        admin.setUpdatedAt(java.time.LocalDateTime.of(2024, 1, 1, 12, 0));
        mockUsers.add(admin);
        
        UserDTO user = new UserDTO();
        user.setId(2L);
        user.setUsername("user");
        user.setEmail("user@example.com");
        user.setPhone("");
        user.setStatus(1);
        user.setCreatedAt(java.time.LocalDateTime.of(2024, 1, 1, 12, 0));
        user.setUpdatedAt(java.time.LocalDateTime.of(2024, 1, 1, 12, 0));
        mockUsers.add(user);
        
        return mockUsers;
    }

    /**
     * 用户列表页面
     */
    @GetMapping("/users")
    public String userList(Model model, HttpSession session) {
        log.info("访问用户管理页面");
        
        // 检查登录状态
        if (session.getAttribute("username") == null) {
            return "redirect:/login";
        }
        
        // 检查管理员权限
        if (!isAdmin(session)) {
            model.addAttribute("error", "权限不足，只有管理员可以访问此页面");
            return "redirect:/";
        }

        try {
            // 调用user-service获取用户列表
            ResponseEntity<List<UserDTO>> response = restTemplate.exchange(
                USER_SERVICE_URL,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<UserDTO>>() {}
            );
            
            List<UserDTO> users = response.getBody();
            if (users == null) {
                users = Collections.emptyList();
            }
            
            // 为每个用户添加角色信息
            Map<Long, Boolean> userAdminMap = new HashMap<>();
            for (UserDTO user : users) {
                boolean isUserAdmin = checkUserIsAdmin(user.getId());
                userAdminMap.put(user.getId(), isUserAdmin);
            }
            
            model.addAttribute("users", users);
            model.addAttribute("userAdminMap", userAdminMap);
            log.info("获取用户列表成功，共{}个用户", users.size());
            
        } catch (Exception e) {
            log.error("获取用户列表失败", e);
            
            // 如果user-service不可用，提供模拟数据用于测试
            List<UserDTO> mockUsers = createMockUsers();
            model.addAttribute("users", mockUsers);
            
            // 为模拟数据添加角色信息
            Map<Long, Boolean> mockUserAdminMap = new HashMap<>();
            mockUserAdminMap.put(1L, true);  // admin用户为管理员
            mockUserAdminMap.put(2L, false); // user用户为普通用户
            model.addAttribute("userAdminMap", mockUserAdminMap);
            
            model.addAttribute("warning", "用户服务连接失败，显示模拟数据。请检查user-service是否启动。");
            log.warn("使用模拟用户数据，user-service可能未启动");
        }
        
        return "admin/user-list";
    }

    /**
     * 新增用户页面
     */
    @GetMapping("/users/new")
    public String newUser(Model model, HttpSession session) {
        log.info("访问新增用户页面");
        
        // 检查登录状态和权限
        if (session.getAttribute("username") == null) {
            return "redirect:/login";
        }
        if (!isAdmin(session)) {
            return "redirect:/";
        }

        model.addAttribute("user", new UserDTO());
        model.addAttribute("isEdit", false);
        model.addAttribute("isUserAdmin", false); // 新用户默认不是管理员
        return "admin/user-form";
    }

    /**
     * 编辑用户页面
     */
    @GetMapping("/users/{id}/edit")
    public String editUser(@PathVariable Long id, Model model, HttpSession session) {
        log.info("访问编辑用户页面，用户ID: {}", id);
        
        // 检查登录状态和权限
        if (session.getAttribute("username") == null) {
            return "redirect:/login";
        }
        if (!isAdmin(session)) {
            return "redirect:/";
        }

        try {
            // 获取用户信息
            ResponseEntity<UserDTO> response = restTemplate.getForEntity(
                USER_SERVICE_URL + "/" + id, UserDTO.class
            );
            
            UserDTO user = response.getBody();
            if (user != null) {
                // 清空密码，避免在表单中显示
                user.setPassword("");
                model.addAttribute("user", user);
                model.addAttribute("isEdit", true);
                
                // 检查用户是否为管理员
                boolean isUserAdmin = checkUserIsAdmin(user.getId());
                model.addAttribute("isUserAdmin", isUserAdmin);
                
                log.info("获取用户信息成功: {}, 是否管理员: {}", user.getUsername(), isUserAdmin);
            } else {
                model.addAttribute("error", "用户不存在");
                return "redirect:/admin/users";
            }
            
        } catch (Exception e) {
            log.error("获取用户信息失败", e);
            model.addAttribute("error", "获取用户信息失败：" + e.getMessage());
            return "redirect:/admin/users";
        }
        
        return "admin/user-form";
    }

    /**
     * 保存用户（新增或更新）
     */
    @PostMapping("/users/save")
    public String saveUser(@ModelAttribute UserDTO user, 
                          @RequestParam(value = "isEdit", defaultValue = "false") boolean isEdit,
                          @RequestParam(value = "isAdmin", defaultValue = "false") boolean isAdmin,
                          Model model, HttpSession session) {
        log.info("保存用户：{}, 编辑模式：{}, 设为管理员：{}", user.getUsername(), isEdit, isAdmin);
        
        // 检查登录状态和权限
        if (session.getAttribute("username") == null) {
            return "redirect:/login";
        }
        if (!isAdmin(session)) {
            return "redirect:/";
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            if (isEdit && user.getId() != null) {
                // 更新用户
                HttpEntity<UserDTO> request = new HttpEntity<>(user, headers);
                restTemplate.exchange(
                    USER_SERVICE_URL + "/" + user.getId(),
                    HttpMethod.PUT,
                    request,
                    UserDTO.class
                );
                log.info("用户更新成功: {}", user.getUsername());
                
                // 处理管理员角色设置
                updateUserRole(user.getId(), isAdmin);
            } else {
                // 新增用户
                ResponseEntity<UserDTO> response = restTemplate.postForEntity(USER_SERVICE_URL, 
                    new HttpEntity<>(user, headers), UserDTO.class);
                UserDTO createdUser = response.getBody();
                log.info("用户创建成功: {}", user.getUsername());
                
                // 为新用户设置角色
                if (createdUser != null) {
                    updateUserRole(createdUser.getId(), isAdmin);
                }
            }
            
            return "redirect:/admin/users";
            
        } catch (Exception e) {
            log.error("保存用户失败", e);
            String errorMsg = e.getMessage();
            if (errorMsg.contains("409")) {
                errorMsg = "用户名已存在";
            } else if (errorMsg.contains("400")) {
                errorMsg = "输入信息不正确";
            } else {
                errorMsg = "保存失败：" + errorMsg;
            }
            
            model.addAttribute("error", errorMsg);
            model.addAttribute("user", user);
            model.addAttribute("isEdit", isEdit);
            return "admin/user-form";
        }
    }

    /**
     * 删除用户
     */
    @PostMapping("/users/{id}/delete")
    @ResponseBody
    public ResponseEntity<String> deleteUser(@PathVariable Long id, HttpSession session) {
        log.info("删除用户，ID: {}", id);
        
        // 检查登录状态和权限
        if (session.getAttribute("username") == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("未登录");
        }
        if (!isAdmin(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("权限不足");
        }

        try {
            // 调用user-service删除用户
            restTemplate.delete(USER_SERVICE_URL + "/" + id);
            log.info("用户删除成功，ID: {}", id);
            return ResponseEntity.ok("删除成功");
            
        } catch (Exception e) {
            log.error("删除用户失败，ID: {}", id, e);
            String errorMsg = "删除失败";
            HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
            
            if (e.getMessage().contains("404")) {
                errorMsg = "用户不存在";
                status = HttpStatus.NOT_FOUND;
            } else if (e.getMessage().contains("403")) {
                errorMsg = "权限不足";
                status = HttpStatus.FORBIDDEN;
            } else if (e.getMessage().contains("400")) {
                errorMsg = "无法删除该用户";
                status = HttpStatus.BAD_REQUEST;
            }
            
            return ResponseEntity.status(status).body(errorMsg);
        }
    }

    /**
     * 批量删除用户
     */
    @PostMapping("/users/batch-delete")
    public String batchDeleteUsers(@RequestParam("userIds") List<Long> userIds, HttpSession session) {
        log.info("批量删除用户，数量: {}", userIds.size());
        
        // 检查登录状态和权限
        if (session.getAttribute("username") == null) {
            return "redirect:/login";
        }
        if (!isAdmin(session)) {
            return "redirect:/";
            }

        try {
            int successCount = 0;
            int failCount = 0;
            
            // 循环删除每个用户
            for (Long userId : userIds) {
                try {
                    restTemplate.delete(USER_SERVICE_URL + "/" + userId);
                    successCount++;
                    log.info("删除用户成功，ID: {}", userId);
                } catch (Exception e) {
                    failCount++;
                    log.warn("删除用户失败，ID: {}, 原因: {}", userId, e.getMessage());
                }
            }
            
            String message = String.format("删除完成：成功%d个，失败%d个", successCount, failCount);
            log.info("批量删除完成：{}", message);
            
            return "redirect:/admin/users";
            
        } catch (Exception e) {
            log.error("批量删除用户失败", e);
            return "redirect:/admin/users?error=批量删除失败：" + e.getMessage();
        }
    }

    /**
     * 用户详情页面
     */
    @GetMapping("/users/{id}")
    public String userDetail(@PathVariable Long id, Model model, HttpSession session) {
        log.info("查看用户详情，ID: {}", id);
        
        // 检查登录状态和权限
        if (session.getAttribute("username") == null) {
            return "redirect:/login";
        }
        if (!isAdmin(session)) {
            return "redirect:/";
        }

        try {
            // 获取用户信息
            ResponseEntity<UserDTO> response = restTemplate.getForEntity(
                USER_SERVICE_URL + "/" + id, UserDTO.class
            );
            
            UserDTO user = response.getBody();
            if (user != null) {
                model.addAttribute("user", user);
                
                // 检查用户是否为管理员
                boolean isUserAdmin = checkUserIsAdmin(user.getId());
                model.addAttribute("isUserAdmin", isUserAdmin);
                
                log.info("获取用户详情成功: {}, 是否管理员: {}", user.getUsername(), isUserAdmin);
            } else {
                model.addAttribute("error", "用户不存在");
                return "redirect:/admin/users";
            }
            
        } catch (Exception e) {
            log.error("获取用户详情失败，ID: {}", id, e);
            model.addAttribute("error", "获取用户信息失败：" + e.getMessage());
            return "redirect:/admin/users";
        }
        
        return "admin/user-detail";
    }

    /**
     * 修复用户角色
     */
    @PostMapping("/users/fix-roles")
    @ResponseBody
    public ResponseEntity<String> fixUserRoles(HttpSession session) {
        log.info("执行用户角色修复");
        
        // 检查登录状态和权限
        if (session.getAttribute("username") == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("未登录");
        }
        if (!isAdmin(session)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("权限不足");
        }

        try {
            // 调用user-service的修复API
            String fixUrl = "http://localhost:8082/api/users/fix-roles";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            restTemplate.exchange(fixUrl, HttpMethod.POST, new HttpEntity<>(headers), String.class);
            log.info("用户角色修复完成");
            return ResponseEntity.ok("角色修复完成");
            
        } catch (Exception e) {
            log.error("用户角色修复失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("修复失败：" + e.getMessage());
        }
    }

} 