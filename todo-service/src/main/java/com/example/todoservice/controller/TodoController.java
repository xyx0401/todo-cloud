package com.example.todoservice.controller;

import com.example.todoservice.entity.TodoItem;
import com.example.todoservice.service.TodoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;
import com.example.todoservice.viewmodel.TodoListViewModel;

import javax.servlet.http.HttpSession;
import java.util.List;
import java.util.Optional;

/**
 * Todo项目REST API控制器
 * 提供完整的增删改查功能
 */
@RestController
@RequestMapping("/api/todos")
@CrossOrigin(origins = "*")
public class TodoController {
    
    private static final Logger log = LoggerFactory.getLogger(TodoController.class);
    
    @Autowired
    private TodoService todoService;

    /**
     * 获取当前用户的所有待办事项
     */
    @GetMapping
    public ResponseEntity<List<TodoItem>> getAllTodos(HttpSession session) {
        try {
            log.info("获取所有待办事项");
            List<TodoItem> todos = todoService.findAll();
            return ResponseEntity.ok(todos);
        } catch (Exception e) {
            log.error("获取待办事项列表失败", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 获取当前用户的所有待办事项（兼容前端调用）
     */
    @GetMapping("/all")
    public ResponseEntity<List<TodoItem>> getAllTodosCompat(HttpSession session) {
        return getAllTodos(session);
    }

    /**
     * 根据ID获取待办事项
     */
    @GetMapping("/{id}")
    public ResponseEntity<TodoItem> getTodoById(@PathVariable Long id) {
        try {
            log.info("根据ID获取待办事项: {}", id);
            Optional<TodoItem> todo = todoService.findById(id);
            if (todo.isPresent()) {
                return ResponseEntity.ok(todo.get());
            } else {
                log.warn("待办事项不存在: {}", id);
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("获取待办事项失败: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 创建新的待办事项
     */
    @PostMapping
    public ResponseEntity<TodoItem> createTodo(@RequestBody TodoItem todoItem) {
        try {
            log.info("创建新待办事项: {}", todoItem.getTitle());
            TodoItem savedTodo = todoService.save(todoItem);
            return ResponseEntity.ok(savedTodo);
        } catch (Exception e) {
            log.error("创建待办事项失败", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 添加待办事项（兼容前端表单提交）
     */
    @PostMapping("/add")
    public ResponseEntity<TodoItem> addTodo(@RequestBody TodoItem todoItem) {
        return createTodo(todoItem);
    }

    /**
     * 更新待办事项
     */
    @PutMapping("/{id}")
    public ResponseEntity<TodoItem> updateTodo(@PathVariable Long id, @RequestBody TodoItem todoItem) {
        try {
            log.info("更新待办事项: {}", id);
            todoItem.setId(id);
            TodoItem updatedTodo = todoService.save(todoItem);
            return ResponseEntity.ok(updatedTodo);
        } catch (Exception e) {
            log.error("更新待办事项失败: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 更新待办事项（兼容前端调用）
     */
    @PostMapping("/update")
    public ResponseEntity<String> updateTodos(@RequestBody List<TodoItem> todoItems) {
        try {
            log.info("批量更新待办事项，数量: {}", todoItems.size());
            for (TodoItem item : todoItems) {
                todoService.save(item);
            }
            return ResponseEntity.ok("更新成功");
        } catch (Exception e) {
            log.error("批量更新待办事项失败", e);
            return ResponseEntity.badRequest().body("更新失败: " + e.getMessage());
        }
    }

    /**
     * 删除待办事项
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteTodo(@PathVariable Long id) {
        try {
            log.info("删除待办事项: {}", id);
            todoService.deleteById(id);
            return ResponseEntity.ok("删除成功");
        } catch (Exception e) {
            log.error("删除待办事项失败: {}", id, e);
            return ResponseEntity.badRequest().body("删除失败: " + e.getMessage());
        }
    }

    /**
     * 切换待办事项完成状态
     */
    @PutMapping("/{id}/toggle")
    public ResponseEntity<TodoItem> toggleTodo(@PathVariable Long id) {
        try {
            log.info("切换待办事项状态: {}", id);
            Optional<TodoItem> todoOpt = todoService.findById(id);
            if (todoOpt.isPresent()) {
                TodoItem todo = todoOpt.get();
                todo.setCompleted(!todo.getCompleted());
                TodoItem updatedTodo = todoService.save(todo);
                return ResponseEntity.ok(updatedTodo);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("切换待办事项状态失败: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 获取待办事项统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<Object> getTodoStats() {
        try {
            List<TodoItem> todos = todoService.findAll();
            long totalCount = todos.size();
            long completedCount = todos.stream().filter(TodoItem::getCompleted).count();
            long pendingCount = totalCount - completedCount;
            
            return ResponseEntity.ok(new Object() {
                public final long total = totalCount;
                public final long completed = completedCount;
                public final long pending = pendingCount;
            });
        } catch (Exception e) {
            log.error("获取待办事项统计失败", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping({"/", "/index"})
    public String index(Model model) {
        TodoListViewModel items = new TodoListViewModel(todoService.findAll());
        model.addAttribute("items", items);
        model.addAttribute("newitem", new TodoItem());
        return "index";
    }
} 