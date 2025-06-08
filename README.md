# Spring Cloud 微服务化 Todo 项目

这是一个基于Spring Cloud的微服务化待办事项管理系统，将原单体Spring Boot应用拆分为多个独立的微服务。

## 项目架构

### 技术栈
- **Spring Boot**: 2.7.18
- **Spring Cloud Alibaba**: 2021.1
- **Nacos**: 1.4.3 (服务注册与发现、配置管理)
- **Spring Cloud Gateway**: 网关路由
- **Spring Security**: 安全认证
- **MySQL**: 数据持久化
- **Thymeleaf**: 页面模板引擎
- **Maven**: 项目构建工具

### 微服务架构
```
┌─────────────────┐    ┌─────────────────┐
│   前端用户      │────│  Gateway网关     │
│   (浏览器)      │    │   (8080端口)    │
└─────────────────┘    └─────────────────┘
                              │
                    ┌─────────┼─────────┐
                    │         │         │
            ┌───────▼───┐ ┌───▼───┐ ┌───▼───┐
            │Todo服务   │ │用户服务│ │认证服务│
            │(8081端口) │ │(8082) │ │(8083) │
            └───────────┘ └───────┘ └───────┘
                    │         │         │
                    └─────────┼─────────┘
                              │
                    ┌─────────▼─────────┐
                    │   Nacos注册中心    │
                    │   (8848端口)      │
                    └───────────────────┘
```

### 服务说明

#### 1. Gateway Service (网关服务) - 端口8080
- **功能**: 统一入口，路由转发，负载均衡，CORS处理
- **路由配置**:
  - `/` → todo-service (首页)
  - `/login` → todo-service (登录页面)
  - `/index` → todo-service (主页面)
  - `/api/todos/**` → todo-service (Todo API)
  - `/api/users/**` → user-service (用户API)
  - `/api/auth/**` → auth-service (认证API)
  - `/admin/**` → todo-service (管理员页面)

#### 2. Todo Service (待办事项服务) - 端口8081
- **功能**: 待办事项的CRUD操作，页面渲染，用户会话管理
- **数据库**: tododb
- **主要接口**:
  - `GET /` - 首页
  - `GET /login` - 登录页面
  - `POST /login` - 登录处理
  - `GET /index` - 待办事项列表页面
  - `GET /api/todos` - 获取当前用户所有待办事项
  - `GET /api/todos/{id}` - 根据ID获取待办事项
  - `POST /api/todos` - 创建新待办事项
  - `PUT /api/todos/{id}` - 更新待办事项
  - `DELETE /api/todos/{id}` - 删除待办事项
  - `PUT /api/todos/{id}/toggle` - 切换完成状态
  - `GET /api/todos/stats` - 获取统计信息

#### 3. User Service (用户服务) - 端口8082
- **功能**: 用户信息管理，用户CRUD操作
- **数据库**: userdb
- **主要接口**:
  - `GET /api/users` - 获取所有用户
  - `GET /api/users/{id}` - 根据ID获取用户信息
  - `GET /api/users/username/{username}` - 根据用户名获取用户信息
  - `GET /api/users/check/{username}` - 检查用户名是否存在
  - `POST /api/users` - 创建新用户
  - `PUT /api/users/{id}` - 更新用户信息
  - `DELETE /api/users/{id}` - 删除用户
  - `DELETE /api/users/batch` - 批量删除用户
  - `GET /api/users/stats` - 获取用户统计信息

#### 4. Auth Service (认证服务) - 端口8083
- **功能**: 用户认证，JWT令牌管理
- **主要接口**:
  - `POST /api/auth/login` - 用户登录
  - `POST /api/auth/validate` - 令牌验证

## 数据库设计

### 用户数据库 (userdb)
```sql
-- 用户表
CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    email VARCHAR(100),
    phone VARCHAR(20),
    status TINYINT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 角色表
CREATE TABLE roles (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 用户角色关联表
CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_id)
);
```

### 待办事项数据库 (tododb)
```sql
-- 待办事项表
CREATE TABLE todo_items (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    completed BOOLEAN DEFAULT FALSE,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

## 快速开始

### 前置条件
1. **Java 21** (项目使用Java 21编译)
2. **Maven 3.6+**
3. **MySQL 8.0+**
4. **Nacos 1.4.3**

### 1. 数据库初始化
```bash
# 在MySQL中执行初始化脚本
mysql -u root -p < init.sql
```

初始化后会创建以下测试账户：
- 管理员: `admin` / `123456`
- 普通用户: `user` / `123456`

### 2. 启动Nacos
```bash
# 下载并启动Nacos
# Windows
startup.cmd -m standalone

# Linux/Mac
sh startup.sh -m standalone
```

Nacos控制台: http://localhost:8848/nacos (nacos/nacos)

### ⚠️ 重要：Nacos配置说明

本项目使用Nacos作为服务注册与发现中心，所有微服务都会自动注册到Nacos，网关通过服务名进行负载均衡路由。

**Nacos配置特点：**
- ✅ 启用服务注册与发现
- ✅ 使用`lb://service-name`进行负载均衡
- ✅ 网关自动发现服务实例
- ✅ 支持服务健康检查
- ✅ 动态配置管理

**验证Nacos注册：**
启动所有服务后，访问 http://localhost:8848/nacos ，在服务管理中应该看到：
- auth-service (8083)
- user-service (8082)
- todo-service (8081)
- gateway-service (8080)

### 3. 启动微服务

**⭐ 推荐方式：使用启动脚本**
```bash
# 一键启动所有服务（包括Nacos检查和服务监控）
start-with-nacos.bat
```

**方式二：使用Maven命令分别启动**
```bash
# 进入项目根目录
cd todo-cloud

# 按顺序启动各服务（重要：必须按顺序启动）
cd auth-service && mvn spring-boot:run     # 1. 认证服务 (8083)
cd user-service && mvn spring-boot:run     # 2. 用户服务 (8082)
cd todo-service && mvn spring-boot:run     # 3. Todo服务 (8081)
cd gateway-service && mvn spring-boot:run  # 4. 网关服务 (8080)
```

**方式三：使用IDE启动**
- 按顺序运行各服务的主启动类：
  1. AuthServiceApplication (8083)
  2. UserServiceApplication (8082)
  3. TodoServiceApplication (8081)
  4. GatewayServiceApplication (8080)

### 4. 访问应用
- 应用首页: http://localhost:8080
- 登录页面: http://localhost:8080/login
- 管理员页面: http://localhost:8080/admin/users (仅admin用户)
- Nacos控制台: http://localhost:8848/nacos

## API文档

### Todo服务API

#### 获取所有待办事项
```http
GET /api/todos
Authorization: Session Cookie
```

#### 创建待办事项
```http
POST /api/todos
Content-Type: application/json

{
  "title": "学习Spring Cloud",
  "description": "完成微服务架构学习",
    "completed": false
}
```

#### 更新待办事项
```http
PUT /api/todos/{id}
Content-Type: application/json

{
  "title": "学习Spring Cloud",
  "description": "完成微服务架构学习",
    "completed": true
}
```

#### 删除待办事项
```http
DELETE /api/todos/{id}
```

#### 切换完成状态
```http
PUT /api/todos/{id}/toggle
```

#### 获取统计信息
```http
GET /api/todos/stats
```

### 用户服务API

#### 获取所有用户
```http
GET /api/users
```

#### 创建用户
```http
POST /api/users
Content-Type: application/json

{
  "username": "newuser",
  "password": "password123"
}
```

#### 更新用户
```http
PUT /api/users/{id}
Content-Type: application/json

{
  "username": "updateduser",
  "password": "newpassword123"
}
```

#### 删除用户
```http
DELETE /api/users/{id}
```

#### 检查用户名是否存在
```http
GET /api/users/check/{username}
```

## 功能特性

### ✅ 已实现功能

1. **微服务架构**
   - 服务注册与发现 (Nacos)
   - API网关路由 (Spring Cloud Gateway)
   - 负载均衡
   - 服务间通信

2. **待办事项管理**
   - ✅ 增加待办事项
   - ✅ 删除待办事项
   - ✅ 修改待办事项
   - ✅ 查询待办事项
   - ✅ 切换完成状态
   - ✅ 统计信息

3. **用户管理**
   - ✅ 用户注册
   - ✅ 用户登录
   - ✅ 用户信息管理
   - ✅ 管理员功能
   - ✅ 用户权限控制

4. **系统功能**
   - ✅ 会话管理
   - ✅ 跨域处理
   - ✅ 错误处理
   - ✅ 日志记录
   - ✅ 健康检查

### 🔧 技术特点

- **数据隔离**: 每个用户只能看到自己的待办事项
- **会话管理**: 基于Session的用户认证
- **错误处理**: 完善的异常处理和错误响应
- **日志记录**: 详细的操作日志和调试信息
- **CORS支持**: 完整的跨域资源共享配置
- **负载均衡**: 基于Nacos的服务发现和负载均衡

## 测试说明

### 功能测试步骤

1. **启动所有服务**
   ```bash
   # 确保Nacos已启动
   # 按顺序启动所有微服务
   ```

2. **测试用户登录**
   - 访问: http://localhost:8080
   - 使用测试账户登录: admin/123456 或 user/123456

3. **测试待办事项CRUD**
   - 添加新任务
   - 修改任务状态
   - 删除任务
   - 查看任务列表

4. **测试管理员功能**
   - 使用admin账户登录
   - 访问: http://localhost:8080/admin/users
   - 测试用户管理功能

5. **测试API接口**
   ```bash
   # 获取待办事项
   curl -X GET http://localhost:8080/api/todos
   
   # 创建待办事项
   curl -X POST http://localhost:8080/api/todos \
     -H "Content-Type: application/json" \
     -d '{"title":"测试任务","description":"API测试"}'
```

## 故障排除

### 常见问题

1. **服务无法启动**
   - 检查端口是否被占用
   - 确认Nacos已正常启动
   - 检查数据库连接配置

2. **页面无法访问**
   - 确认网关服务已启动
   - 检查路由配置
   - 查看网关日志

3. **数据库连接失败**
   - 确认MySQL服务已启动
   - 检查数据库配置
   - 确认数据库已初始化

4. **服务注册失败**
   - 检查Nacos连接配置
   - 确认网络连通性
   - 查看服务启动日志

### 日志查看

- 网关日志: 查看请求路由情况
- 服务日志: 查看业务处理情况
- Nacos日志: 查看服务注册情况

## 项目结构

```
todo-cloud/
├── gateway-service/          # 网关服务
├── todo-service/            # 待办事项服务
├── user-service/            # 用户服务
├── auth-service/            # 认证服务
└── pom.xml                  # 父项目配置
```

## 开发说明

### 添加新功能

1. 在相应的服务中添加业务逻辑
2. 更新API文档
3. 添加相应的测试
4. 更新网关路由配置（如需要）

### 部署说明

项目支持多种部署方式：
- 本地开发环境
- Docker容器化部署
- K3s集群部署

详细部署文档请参考：
- [K3s部署文档](k3s-deployment.md)
- [CI/CD流水线](ci-cd-pipeline.md)

## 贡献指南

1. Fork项目
2. 创建功能分支
3. 提交更改
4. 创建Pull Request

## 许可证

本项目采用MIT许可证。

## 联系方式

如有问题或建议，请通过以下方式联系：
- 提交 Issue
- 发送邮件

---

**注意**: 这是一个学习项目，用于演示Spring Cloud微服务架构的实现。在生产环境中使用时，请根据实际需求进行安全加固和性能优化。 