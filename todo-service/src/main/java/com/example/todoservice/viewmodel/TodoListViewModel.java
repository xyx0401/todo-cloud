package com.example.todoservice.viewmodel;

import com.example.todoservice.entity.TodoItem;
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;

public class TodoListViewModel {
    @Valid
    private List<TodoItem> todoList = new ArrayList<>();

    public TodoListViewModel() {}

    public TodoListViewModel(Iterable<TodoItem> items) {
        items.forEach(todoList::add);
    }

    public TodoListViewModel(List<TodoItem> todoList) {
        this.todoList = todoList;
    }

    public List<TodoItem> getTodoList() {
        return todoList;
    }

    public void setTodoList(List<TodoItem> todoList) {
        this.todoList = todoList;
    }
} 