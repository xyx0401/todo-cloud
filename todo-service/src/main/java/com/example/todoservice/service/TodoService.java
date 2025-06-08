package com.example.todoservice.service;

import com.example.todoservice.entity.TodoItem;
import com.example.todoservice.repository.TodoItemRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpSession;
import java.util.List;
import java.util.Optional;

@Service
public class TodoService {
    private static final Logger log = LoggerFactory.getLogger(TodoService.class);
    @Autowired
    private TodoItemRepository todoItemRepository;

    /**
     * 获取当前登录用户ID（从Session中获取）
     */
    private Long getCurrentUserId() {
        try {
            ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            HttpSession session = attr.getRequest().getSession(false);
            
            if (session != null) {
                Long userId = (Long) session.getAttribute("userId");
                if (userId != null) {
                    log.info("从Session获取到用户ID: {}", userId);
                    return userId;
                }
            }
            
            log.error("未登录或Session中无用户ID");
            throw new RuntimeException("未登录，请先登录");
        } catch (Exception e) {
            log.error("获取用户ID失败: {}", e.getMessage());
            throw new RuntimeException("未登录，请先登录");
        }
    }

    public List<TodoItem> findAll() {
        Long userId = getCurrentUserId();
        log.info("查询用户{}的所有任务", userId);
        return todoItemRepository.findByUserId(userId);
    }

    public Optional<TodoItem> findById(Long id) {
        log.info("根据ID查询任务: {}", id);
        Optional<TodoItem> todoItem = todoItemRepository.findById(id);
        // 检查任务是否属于当前用户
        if (todoItem.isPresent()) {
            Long userId = getCurrentUserId();
            if (!userId.equals(todoItem.get().getUserId())) {
                log.warn("用户{}尝试访问不属于自己的任务{}", userId, id);
                return Optional.empty();
            }
        }
        return todoItem;
    }

    public TodoItem save(TodoItem todoItem) {
        Long userId = getCurrentUserId();
        todoItem.setUserId(userId);
        log.info("保存用户{}的任务: {} (id={})", userId, todoItem.getName(), todoItem.getId());
        return todoItemRepository.save(todoItem);
    }

    public void deleteById(Long id) {
        Optional<TodoItem> todoItem = findById(id);
        if (todoItem.isPresent()) {
            log.info("删除任务: {}", id);
            todoItemRepository.deleteById(id);
        } else {
            log.warn("任务不存在或无权限删除: {}", id);
            throw new RuntimeException("任务不存在或无权限删除");
        }
    }
} 