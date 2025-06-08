package com.example.todoservice.controller;

import com.example.todoservice.entity.TodoItem;
import com.example.todoservice.service.TodoService;
import com.example.todoservice.viewmodel.TodoListViewModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import javax.servlet.http.HttpSession;

@Controller
public class TodoPageController {

    private static final Logger log = LoggerFactory.getLogger(TodoPageController.class);

    @Autowired
    private TodoService todoService;

    @GetMapping({"/", "/index"})
    public String index(Model model, HttpSession session) {
        log.info("访问首页");
        
        // 检查用户登录状态
        String username = (String) session.getAttribute("username");
        Long userId = (Long) session.getAttribute("userId");
        
        if (username == null || userId == null) {
            log.info("用户未登录，重定向到登录页面");
            return "redirect:/login";
        }
        
        log.info("用户 {} (ID: {}) 访问主页", username, userId);
        
        try {
            // 获取当前用户的待办事项
            TodoListViewModel items = new TodoListViewModel(todoService.findAll());
            model.addAttribute("items", items);
            model.addAttribute("newitem", new TodoItem());
            return "index";
        } catch (Exception e) {
            log.error("获取待办事项列表失败", e);
            model.addAttribute("error", "获取数据失败，请重新登录");
            return "redirect:/logout";
        }
    }

    /**
     * 获取当前登录用户ID（从Session中获取）
     */
    private Long getCurrentUserId(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId != null) {
            log.info("从Session获取到用户ID: {}", userId);
            return userId;
        }
        log.error("Session中无用户ID，用户未登录");
        throw new RuntimeException("未登录，请先登录");
    }

    @PostMapping("/add")
    public String add(@ModelAttribute("newitem") TodoItem requestItem, HttpSession session) {
        log.info("添加任务: {}", requestItem.getName());
        requestItem.setUserId(getCurrentUserId(session));
        todoService.save(requestItem);
        return "redirect:/";
    }

    @PostMapping("/update")
    public String update(@ModelAttribute("items") TodoListViewModel items, HttpSession session) {
        Long userId = getCurrentUserId(session);
        for (TodoItem item : items.getTodoList()) {
            log.info("更新任务: {} (id={})", item.getName(), item.getId());
            item.setUserId(userId);
            todoService.save(item);
        }
        return "redirect:/";
    }

    @GetMapping("/delete/{id}")
    public String delete(@PathVariable Long id, HttpSession session) {
        Long userId = getCurrentUserId(session);
        try {
            // 先检查任务是否属于当前用户
            TodoItem existingItem = todoService.findById(id).orElse(null);
            if (existingItem == null) {
                log.warn("任务不存在: {}", id);
                return "redirect:/";
            }
            
            if (!existingItem.getUserId().equals(userId)) {
                log.warn("用户{}试图删除不属于自己的任务: {}", userId, id);
                return "redirect:/";
            }
            
            log.info("删除任务: {} (id={}, userId={})", existingItem.getName(), id, userId);
            todoService.deleteById(id);
        } catch (Exception e) {
            log.error("删除任务失败: {}", id, e);
        }
        
        // 无论成功失败都直接重定向到主页，保持简洁
        return "redirect:/";
    }
}