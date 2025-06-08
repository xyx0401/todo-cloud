package com.example.todoservice.repository;

import com.example.todoservice.entity.TodoItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
 
public interface TodoItemRepository extends JpaRepository<TodoItem, Long> {
    List<TodoItem> findByUserId(Long userId);
} 