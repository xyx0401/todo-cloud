package com.example.todoservice.controller;

import com.example.todoservice.entity.TodoItem;
import com.example.todoservice.service.TodoService;
import com.example.todoservice.viewmodel.TodoListViewModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;

/**
 * Todo兼容性API控制器
 * 提供与前端页面兼容的API接口
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class TodoCompatController {
    
    private static final Logger log = LoggerFactory.getLogger(TodoCompatController.class);
    
    @Autowired
    private TodoService todoService;



    /**
     * 删除待办事项（兼容性接口）
     */
    @DeleteMapping("/todos/delete/{id}")
    public ResponseEntity<String> deleteTodo(@PathVariable Long id, HttpSession session) {
        try {
            log.info("兼容性API：删除待办事项 - {}", id);
            todoService.deleteById(id);
            return ResponseEntity.ok("删除成功");
        } catch (Exception e) {
            log.error("删除待办事项失败: {}", id, e);
            return ResponseEntity.badRequest().body("删除失败: " + e.getMessage());
    }
    }




} 