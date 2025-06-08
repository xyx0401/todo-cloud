# Spring Cloud 微服务架构设计

## 系统架构概览

### 微服务架构图

```
                    ┌─────────────────┐
                    │   前端用户      │
                    │   (浏览器)      │
                    └─────────┬───────┘
                              │ HTTP请求
                              │
                    ┌─────────▼───────┐
                    │  Gateway网关     │
                    │   (8080端口)    │
                    │ Spring Cloud    │
                    │    Gateway      │
                    └─────────┬───────┘
                              │ 路由转发
                    ┌─────────┼─────────┐
                    │         │         │
            ┌───────▼───┐ ┌───▼───┐ ┌───▼───┐
            │Todo服务   │ │用户服务│ │认证服务│
            │(8081端口) │ │(8082) │ │(8083) │
            │Spring Boot│ │Spring │ │Spring │
            │+ JPA      │ │Boot   │ │Boot   │
            └─────┬─────┘ └───┬───┘ └───┬───┘
                  │           │         │
                  │ 数据访问   │ 数据访问 │ 认证
                  │           │         │
            ┌─────▼─────┐ ┌───▼───┐     │
            │  tododb   │ │userdb │     │
            │  数据库   │ │数据库 │     │
            └───────────┘ └───────┘     │
                                        │
                    ┌───────────────────┘
                    │
                    ▼
          ┌─────────────────┐
          │   Nacos注册中心  │
          │   (8848端口)    │
          │ 服务注册与发现   │
          │   配置管理      │
          └─────────────────┘
```

## 技术栈

### 核心技术
- **Java**: 21 (LTS版本)
- **Spring Boot**: 2.7.18
- **Spring Cloud Alibaba**: 2021.1
- **Maven**: 项目构建工具

### 微服务组件
- **Spring Cloud Gateway**: 网关路由和负载均衡
- **Nacos**: 服务注册与发现、配置管理
- **Spring Cloud LoadBalancer**: 客户端负载均衡

### 数据和安全
- **MySQL**: 8.0+ 数据库
- **Spring Data JPA**: 数据访问层
- **Spring Security**: 安全认证
- **BCrypt**: 密码加密
- **JWT**: 令牌认证

### 模板和工具
- **Thymeleaf**: 页面模板引擎
- **Lombok**: 简化Java代码

## 微服务详细设计

### 1. Gateway Service (网关服务) - 8080端口

**职责**：
- 统一入口和路由管理
- 负载均衡和服务发现
- 跨域处理(CORS)
- 请求转发和流量控制

**技术栈**：
- Spring Cloud Gateway
- Nacos Discovery
- Spring Cloud LoadBalancer

**路由配置**：
```yaml
spring:
  cloud:
    gateway:
      discovery:
        locator:
          enabled: true
          lower-case-service-id: true
      routes:
        # API服务路由 - 高优先级
        - id: user-api
          uri: http://localhost:8082
          predicates:
            - Path=/api/users/**
            
        - id: todo-api
          uri: http://localhost:8081
          predicates:
            - Path=/api/todos/**
            
        - id: auth-api
          uri: http://localhost:8083
          predicates:
            - Path=/api/auth/**
            
        # 管理员页面路由
        - id: admin-pages
          uri: http://localhost:8081
          predicates:
            - Path=/admin/**
            
        # 静态资源和登录页面
        - id: static-resources
          uri: http://localhost:8081
          predicates:
            - Path=/css/**,/js/**,/images/**,/webjars/**
            
        - id: login-routes
          uri: http://localhost:8081
          predicates:
            - Path=/login,/logout,/login/**
            
        # 兜底路由 - 最低优先级
        - id: todo-all
          uri: http://localhost:8081
          predicates:
            - Path=/**
```

### 2. Todo Service (待办事项服务) - 8081端口

**职责**：
- 待办事项CRUD操作
- 页面渲染和用户界面
- 用户会话管理
- 管理员功能

**技术栈**：
- Spring Boot 2.7.18
- Spring Data JPA
- Thymeleaf模板引擎
- MySQL数据库
- Spring Session
- OpenFeign (服务间调用)

**核心功能**：
- Todo增删改查
- 用户登录验证
- 会话管理
- 页面渲染
- 管理员用户管理

**数据库**：tododb

**API设计**：
- RESTful API设计
- JSON数据交换
- 用户权限隔离

### 3. User Service (用户服务) - 8082端口

**职责**：
- 用户信息管理
- 用户CRUD操作
- 密码加密和验证
- 角色权限管理

**技术栈**：
- Spring Boot 2.7.18
- Spring Data JPA
- Spring Security (BCrypt)
- MySQL数据库

**核心功能**：
- 用户增删改查
- 密码BCrypt加密
- 用户信息验证
- 批量操作
- 用户统计

**数据库**：userdb

**数据模型**：
- 用户表(users)
- 角色表(roles)
- 用户角色关联表(user_roles)

### 4. Auth Service (认证服务) - 8083端口

**职责**：
- JWT令牌生成和验证
- 用户认证服务
- 令牌管理

**技术栈**：
- Spring Boot 2.7.18
- JWT (io.jsonwebtoken)
- Spring Security

**核心功能**：
- JWT令牌生成
- 令牌验证
- 用户认证
- 令牌过期管理

**配置**：
- JWT密钥：可配置
- 令牌有效期：24小时

### 5. Nacos Registry (注册中心) - 8848端口

**职责**：
- 服务注册与发现
- 配置管理
- 健康检查
- 负载均衡支持

**功能**：
- 所有微服务自动注册
- 网关服务发现
- 配置统一管理
- 服务健康监控

## 服务交互流程

### 用户登录流程
```
1. 用户访问 → Gateway(8080) → Todo-Service(8081) → 登录页面
2. 提交登录 → Todo-Service → OpenFeign调用User-Service → 验证成功 → 设置Session
3. 重定向到主页
```

### Todo操作流程
```
1. 用户操作 → Gateway(8080) → Todo-Service(8081)
2. Session验证 → JPA操作tododb → 返回JSON数据
3. 页面渲染 → 返回用户
```

### JWT认证流程
```
1. 认证请求 → Gateway(8080) → Auth-Service(8083)
2. 调用User-Service验证 → 生成JWT令牌 → 返回token
3. 后续请求携带token → 验证通过 → 访问资源
```

### 服务发现流程
```
1. 各服务启动 → 注册到Nacos(8848)
2. Gateway通过服务名发现服务实例
3. 负载均衡转发请求
4. 服务健康检查和故障转移
```

## 数据库设计

### tododb数据库
```sql
-- 待办事项表
CREATE TABLE todo_items (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(200) NOT NULL COMMENT '待办事项标题',
    description TEXT COMMENT '待办事项描述',
    completed BOOLEAN DEFAULT FALSE COMMENT '是否完成',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_id (user_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='待办事项表';
```

### userdb数据库
```sql  
-- 用户表
CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(100) NOT NULL COMMENT '密码',
    email VARCHAR(100) COMMENT '邮箱',
    phone VARCHAR(20) COMMENT '手机号',
    status TINYINT DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_username (username),
    INDEX idx_email (email),
    INDEX idx_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 角色表
CREATE TABLE roles (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL UNIQUE COMMENT '角色名称',
    description VARCHAR(200) COMMENT '角色描述',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- 用户角色关联表
CREATE TABLE user_roles (
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';
```

## 技术特性

### 服务注册与发现
- 使用Nacos作为注册中心
- 所有微服务自动注册
- 支持健康检查和故障转移
- 动态服务发现

### 负载均衡
- Spring Cloud LoadBalancer
- 支持多实例部署
- 自动故障检测
- 客户端负载均衡

### 配置管理
- Nacos配置中心
- 动态配置更新
- 环境隔离
- 配置版本管理

### 数据一致性
- 用户隔离：每个用户只能访问自己的Todo
- 会话管理：基于Session的用户状态管理
- 微服务通信：OpenFeign进行服务间调用
- 事务管理：JPA事务支持

### 安全机制
- 密码BCrypt加密
- JWT令牌认证
- Session会话管理
- CORS跨域支持
- 用户权限隔离

## 部署架构

### 开发环境
```
本地开发环境:
├── MySQL(3306) - tododb, userdb
├── Nacos(8848) - 注册中心
├── Gateway(8080) - 网关服务
├── Todo-Service(8081) - 待办事项服务
├── User-Service(8082) - 用户服务
└── Auth-Service(8083) - 认证服务
```

### 生产环境（K3S）
```
K3S集群
├── MySQL Pod
├── Nacos Pod
├── Gateway Pod  
├── Todo Pod (多实例)
├── User Pod (多实例)
└── Auth Pod (多实例)
```

## 扩展性设计

### 水平扩展
- 各微服务支持多实例部署
- 通过Nacos自动负载均衡
- 数据库连接池支持高并发
- 无状态服务设计

### 垂直扩展
- 容器化部署，支持资源配置
- 支持CPU和内存弹性伸缩
- JVM参数可配置优化
- 数据库性能调优

### 功能扩展
- 插件化架构设计
- 微服务独立演进
- API版本管理
- 新功能模块化

## 监控与治理

### 服务监控
- Nacos服务状态监控
- 应用程序日志记录
- 数据库连接监控
- JVM性能监控

### 故障处理
- 服务降级和熔断
- 重试机制
- 健康检查
- 故障转移

### 链路追踪
- 请求链路跟踪
- 性能分析
- 错误定位
- 调用关系分析

## 开发规范

### 代码规范
- 统一编码风格
- 注释规范
- 异常处理规范
- 日志记录规范

### API设计规范
- RESTful API设计
- 统一响应格式
- 错误码规范
- 版本管理

### 数据库规范
- 命名规范
- 索引设计
- 字符集统一
- 事务管理

## 安全设计

### 认证授权
- 多种认证方式支持
- 角色权限控制
- 资源访问控制
- 会话管理

### 数据安全
- 密码加密存储
- 数据传输加密
- SQL注入防护
- XSS攻击防护

### 网络安全
- 防火墙配置
- 端口访问控制
- HTTPS支持
- 网络隔离