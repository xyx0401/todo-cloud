package com.example.todoservice.controller;

import com.example.todoservice.dto.UserDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpSession;

@Controller
public class LoginController {

    private static final Logger log = LoggerFactory.getLogger(LoginController.class);
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping("/login")
    public String loginPage(@RequestParam(value = "error", required = false) String error,
                           @RequestParam(value = "logout", required = false) String logout,
                           Model model) {
        if (error != null) {
            model.addAttribute("error", "用户名或密码错误！");
        }
        if (logout != null) {
            model.addAttribute("message", "您已成功退出登录！");
        }
        
        // 添加测试账户信息
        model.addAttribute("testAccounts", "测试账户：admin/123456 或 user/123456");
        
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String username,
                       @RequestParam String password,
                       HttpSession session,
                       Model model) {
        try {
            // 直接查询数据库验证用户
            UserDTO user = findUserByUsername(username);
            
            if (user != null && verifyPassword(password, user.getPassword())) {
                // 登录成功，保存用户信息到session
                session.setAttribute("currentUser", user);
                session.setAttribute("userId", user.getId());
                session.setAttribute("username", user.getUsername());
                
                log.info("用户登录成功: {}", username);
                return "redirect:/";  // 重定向到主页
            } else {
                log.warn("用户登录失败: {}", username);
                model.addAttribute("error", "用户名或密码错误！");
                model.addAttribute("testAccounts", "测试账户：admin/123456 或 user/123456");
                return "login";
            }
        } catch (Exception e) {
            log.error("登录服务异常", e);
            // 如果数据库异常，使用模拟登录作为后备
            if (("admin".equals(username) || "user".equals(username)) && "123456".equals(password)) {
                UserDTO mockUser = createMockUser(username);
                session.setAttribute("currentUser", mockUser);
                session.setAttribute("userId", mockUser.getId());
                session.setAttribute("username", mockUser.getUsername());
                log.info("使用模拟登录: {}", username);
                return "redirect:/";
            }
            
            model.addAttribute("error", "登录服务异常，请联系管理员");
            model.addAttribute("testAccounts", "测试账户：admin/123456 或 user/123456");
            return "login";
        }
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();  // 清除session
        return "redirect:/login?logout=true";
    }

    /**
     * 根据用户名查询用户
     */
    private UserDTO findUserByUsername(String username) {
        try {
            // 先尝试从userdb查询
            String sql = "SELECT id, username, password FROM userdb.users WHERE username = ?";
            return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
                UserDTO user = new UserDTO();
                user.setId(rs.getLong("id"));
                user.setUsername(rs.getString("username"));
                user.setPassword(rs.getString("password"));
                return user;
            }, username);
        } catch (Exception e) {
            log.warn("数据库查询用户失败: {}", username, e);
            return null;
        }
    }

    /**
     * 验证密码
     */
    private boolean verifyPassword(String rawPassword, String encodedPassword) {
        try {
            // 首先尝试BCrypt验证
            if (encodedPassword.startsWith("$2a$") || encodedPassword.startsWith("$2b$") || encodedPassword.startsWith("$2y$")) {
                return passwordEncoder.matches(rawPassword, encodedPassword);
            }
            // 如果不是BCrypt格式，进行明文比较（为了兼容测试数据）
            return rawPassword.equals(encodedPassword);
        } catch (Exception e) {
            log.warn("密码验证异常", e);
            return false;
        }
    }
    
    // 创建模拟用户用于降级处理
    private UserDTO createMockUser(String username) {
        UserDTO user = new UserDTO();
        if ("admin".equals(username)) {
            user.setId(1L);
            user.setUsername("admin");
            user.setPassword("123456");
        } else if ("user".equals(username)) {
            user.setId(2L);
            user.setUsername("user");
            user.setPassword("123456");
        }
        return user;
    }
} 