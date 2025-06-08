# API 文档

## 概述

Spring Cloud Todo 微服务系统提供了完整的待办事项管理和用户管理功能。本文档详细描述了所有可用的API接口。

## 技术栈

- **Java**: 21
- **Spring Boot**: 2.7.18
- **Spring Cloud Alibaba**: 2021.1
- **Nacos**: 1.4.3
- **MySQL**: 8.0+
- **Maven**: 构建工具

## 服务架构

```
Gateway Service (8080) 
├── Todo Service (8081) - 待办事项和页面
├── User Service (8082) - 用户管理  
└── Auth Service (8083) - 认证服务
     ↑
Nacos Registry (8848) - 服务注册与发现
```

## 1. Gateway Service API

### 基础信息
- **服务端口**: 8080
- **服务名**: gateway-service
- **路由前缀**: 无
- **服务发现**: 通过Nacos自动发现后端服务

### 路由配置

| 路径模式 | 目标服务 | 描述 | 优先级 |
|---------|---------|------|--------|
| `/api/users/**` | user-service | 用户管理API | 高 |
| `/api/todos/**` | todo-service | 待办事项API | 高 |
| `/api/auth/**` | auth-service | 认证API | 高 |
| `/admin/**` | todo-service | 管理员页面 | 高 |
| `/login`, `/logout` | todo-service | 登录相关 | 中 |
| `/css/**`, `/js/**`, `/images/**` | todo-service | 静态资源 | 中 |
| `/**` | todo-service | 所有其他请求 | 低 |

### CORS配置
- 支持跨域访问
- 允许的方法: GET, POST, PUT, DELETE, OPTIONS
- 允许所有Origin和Header
- 支持凭证传递

## 2. Todo Service API

### 基础信息
- **服务端口**: 8081
- **服务名**: todo-service
- **数据库**: tododb
- **基础路径**: `/`

### 2.1 页面路由

#### 获取主页
```http
GET /
GET /index
```

**描述**: 获取待办事项主页，显示当前用户的所有待办事项

**响应**: HTML页面

**状态码**:
- `200 OK` - 成功返回页面
- `302 Found` - 未登录，重定向到登录页

---

#### 获取登录页面
```http
GET /login
```

**描述**: 获取用户登录页面

**响应**: HTML登录表单

**状态码**:
- `200 OK` - 成功返回登录页面

---

#### 获取管理员页面
```http
GET /admin/users
```

**描述**: 获取用户管理页面（管理员功能）

**响应**: HTML管理页面

**状态码**:
- `200 OK` - 成功返回页面
- `403 Forbidden` - 权限不足
- `302 Found` - 未登录，重定向到登录页

### 2.2 用户认证API

#### 用户登录
```http
POST /login
```

**请求格式**: `application/x-www-form-urlencoded`

**请求参数**:
```
username=string&password=string
```

| 参数 | 类型 | 必填 | 描述 |
|------|------|------|------|
| username | string | 是 | 用户名 |
| password | string | 是 | 密码 |

**响应**: 重定向到主页或登录页

**状态码**:
- `302 Found` - 登录成功，重定向到主页
- `302 Found` - 登录失败，重定向到登录页

**示例**:
```bash
curl -X POST http://localhost:8081/login \
     -H "Content-Type: application/x-www-form-urlencoded" \
     -d "username=admin&password=password"
```

---

#### 用户登出
```http
POST /logout
```

**描述**: 用户退出登录

**响应**: 重定向到登录页面

**状态码**:
- `302 Found` - 成功登出，重定向到登录页

### 2.3 待办事项API

#### 获取所有待办事项
```http
GET /api/todos
```

**描述**: 获取当前用户的所有待办事项

**响应格式**: `application/json`

**响应示例**:
```json
[
  {
    "id": 1,
    "title": "学习Spring Cloud",
    "description": "学习微服务架构和Spring Cloud组件",
    "completed": false,
    "userId": 1,
    "createdAt": "2024-01-15T10:30:00",
    "updatedAt": "2024-01-15T10:30:00"
  },
  {
    "id": 2,
    "title": "完成项目文档",
    "description": "编写项目的技术文档和使用说明",
    "completed": true,
    "userId": 1,
    "createdAt": "2024-01-14T09:15:00",
    "updatedAt": "2024-01-15T14:20:00"
  }
]
```

**状态码**:
- `200 OK` - 成功返回待办事项列表
- `401 Unauthorized` - 未登录

---

#### 根据ID获取待办事项
```http
GET /api/todos/{id}
```

**路径参数**:
- `id` (Long) - 待办事项ID

**描述**: 获取指定ID的待办事项详情

**响应格式**: `application/json`

**响应示例**:
```json
{
  "id": 1,
  "title": "学习Spring Cloud",
  "description": "学习微服务架构和Spring Cloud组件",
  "completed": false,
  "userId": 1,
  "createdAt": "2024-01-15T10:30:00",
  "updatedAt": "2024-01-15T10:30:00"
}
```

**状态码**:
- `200 OK` - 成功返回待办事项详情
- `404 Not Found` - 待办事项不存在
- `403 Forbidden` - 无权访问该待办事项
- `401 Unauthorized` - 未登录

---

#### 创建待办事项
```http
POST /api/todos
```

**请求格式**: `application/json`

**请求体**:
```json
{
  "title": "string",
  "description": "string"
}
```

| 字段 | 类型 | 必填 | 描述 |
|------|------|------|------|
| title | string | 是 | 待办事项标题 (最大200字符) |
| description | string | 否 | 待办事项描述 |

**响应格式**: `application/json`

**响应示例**:
```json
{
  "id": 3,
  "title": "学习Kubernetes",
  "description": "深入学习容器编排技术",
  "completed": false,
  "userId": 1,
  "createdAt": "2024-01-15T15:30:00",
  "updatedAt": "2024-01-15T15:30:00"
}
```

**状态码**:
- `201 Created` - 创建成功
- `400 Bad Request` - 参数验证失败
- `401 Unauthorized` - 未登录

**示例**:
```bash
curl -X POST http://localhost:8081/api/todos \
     -H "Content-Type: application/json" \
     -H "Cookie: JSESSIONID=your_session_id" \
     -d '{
       "title": "学习Kubernetes",
       "description": "深入学习容器编排技术"
     }'
```

---

#### 更新待办事项
```http
PUT /api/todos/{id}
```

**路径参数**:
- `id` (Long) - 待办事项ID

**请求格式**: `application/json`

**请求体**:
```json
{
  "title": "string",
  "description": "string",
  "completed": "boolean"
}
```

| 字段 | 类型 | 必填 | 描述 |
|------|------|------|------|
| title | string | 否 | 待办事项标题 |
| description | string | 否 | 待办事项描述 |
| completed | boolean | 否 | 是否完成 |

**响应格式**: `application/json`

**状态码**:
- `200 OK` - 更新成功
- `404 Not Found` - 待办事项不存在
- `403 Forbidden` - 无权修改该待办事项
- `401 Unauthorized` - 未登录

---

#### 删除待办事项
```http
DELETE /api/todos/{id}
```

**路径参数**:
- `id` (Long) - 待办事项ID

**描述**: 删除指定的待办事项

**状态码**:
- `204 No Content` - 删除成功
- `404 Not Found` - 待办事项不存在
- `403 Forbidden` - 无权删除该待办事项
- `401 Unauthorized` - 未登录

---

#### 切换完成状态
```http
PUT /api/todos/{id}/toggle
```

**路径参数**:
- `id` (Long) - 待办事项ID

**描述**: 切换待办事项的完成状态

**响应格式**: `application/json`

**状态码**:
- `200 OK` - 切换成功
- `404 Not Found` - 待办事项不存在
- `403 Forbidden` - 无权修改该待办事项
- `401 Unauthorized` - 未登录

---

#### 获取统计信息
```http
GET /api/todos/stats
```

**描述**: 获取当前用户的待办事项统计信息

**响应格式**: `application/json`

**响应示例**:
```json
{
  "total": 10,
  "completed": 6,
  "pending": 4,
  "completionRate": 0.6
}
```

**状态码**:
- `200 OK` - 成功返回统计信息
- `401 Unauthorized` - 未登录

## 3. User Service API

### 基础信息
- **服务端口**: 8082
- **服务名**: user-service
- **数据库**: userdb
- **基础路径**: `/api/users`

### 3.1 用户管理API

#### 获取所有用户
```http
GET /api/users
```

**描述**: 获取所有用户列表

**响应格式**: `application/json`

**响应示例**:
```json
[
  {
    "id": 1,
    "username": "admin",
    "email": "admin@example.com",
    "phone": null,
    "status": 1,
    "createdAt": "2024-01-10T08:00:00",
    "updatedAt": "2024-01-10T08:00:00"
  },
  {
    "id": 2,
    "username": "user",
    "email": "user@example.com",
    "phone": null,
    "status": 1,
    "createdAt": "2024-01-11T09:30:00",
    "updatedAt": "2024-01-11T09:30:00"
  }
]
```

**状态码**:
- `200 OK` - 成功返回用户列表

---

#### 根据ID获取用户
```http
GET /api/users/{id}
```

**路径参数**:
- `id` (Long) - 用户ID

**描述**: 获取指定ID的用户信息

**响应格式**: `application/json`

**响应示例**:
```json
{
  "id": 1,
  "username": "admin",
  "email": "admin@example.com",
  "phone": null,
  "status": 1,
  "createdAt": "2024-01-10T08:00:00",
  "updatedAt": "2024-01-10T08:00:00"
}
```

**状态码**:
- `200 OK` - 成功返回用户信息
- `404 Not Found` - 用户不存在

---

#### 根据用户名获取用户
```http
GET /api/users/username/{username}
```

**路径参数**:
- `username` (String) - 用户名

**描述**: 根据用户名获取用户信息

**响应格式**: `application/json`

**状态码**:
- `200 OK` - 成功返回用户信息
- `404 Not Found` - 用户不存在

---

#### 检查用户名是否存在
```http
GET /api/users/check/{username}
```

**路径参数**:
- `username` (String) - 用户名

**描述**: 检查用户名是否已存在

**响应格式**: `application/json`

**响应示例**:
```json
{
  "exists": true
}
```

**状态码**:
- `200 OK` - 检查完成

---

#### 创建新用户
```http
POST /api/users
```

**请求格式**: `application/json`

**请求体**:
```json
{
  "username": "string",
  "password": "string",
  "email": "string",
  "phone": "string"
}
```

| 字段 | 类型 | 必填 | 描述 |
|------|------|------|------|
| username | string | 是 | 用户名 (3-50字符，唯一) |
| password | string | 是 | 密码 (6-100字符) |
| email | string | 否 | 电子邮箱 |
| phone | string | 否 | 手机号码 |

**响应格式**: `application/json`

**响应示例**:
```json
{
  "id": 3,
  "username": "newuser",
  "email": "newuser@example.com",
  "phone": "13700137000",
  "status": 1,
  "createdAt": "2024-01-15T15:30:00",
  "updatedAt": "2024-01-15T15:30:00"
}
```

**状态码**:
- `201 Created` - 用户创建成功
- `400 Bad Request` - 参数验证失败
- `409 Conflict` - 用户名已存在

---

#### 更新用户信息
```http
PUT /api/users/{id}
```

**路径参数**:
- `id` (Long) - 用户ID

**请求格式**: `application/json`

**请求体**:
```json
{
  "username": "string",
  "password": "string",
  "email": "string",
  "phone": "string",
  "status": "integer"
}
```

| 字段 | 类型 | 必填 | 描述 |
|------|------|------|------|
| username | string | 否 | 用户名 |
| password | string | 否 | 新密码 |
| email | string | 否 | 电子邮箱 |
| phone | string | 否 | 手机号码 |
| status | integer | 否 | 状态 (1-启用, 0-禁用) |

**响应格式**: `application/json`

**状态码**:
- `200 OK` - 更新成功
- `400 Bad Request` - 参数验证失败
- `404 Not Found` - 用户不存在
- `409 Conflict` - 用户名冲突

---

#### 删除用户
```http
DELETE /api/users/{id}
```

**路径参数**:
- `id` (Long) - 用户ID

**描述**: 删除指定ID的用户

**状态码**:
- `204 No Content` - 删除成功
- `404 Not Found` - 用户不存在

---

#### 批量删除用户
```http
DELETE /api/users/batch
```

**请求格式**: `application/json`

**请求体**:
```json
{
  "ids": [1, 2, 3]
}
```

**描述**: 批量删除指定ID的用户

**状态码**:
- `204 No Content` - 删除成功
- `400 Bad Request` - 参数错误

---

#### 获取用户统计信息
```http
GET /api/users/stats
```

**描述**: 获取用户统计信息

**响应格式**: `application/json`

**响应示例**:
```json
{
  "total": 15,
  "active": 12,
  "inactive": 3,
  "newThisMonth": 5
}
```

**状态码**:
- `200 OK` - 成功返回统计信息

## 4. Auth Service API

### 基础信息
- **服务端口**: 8083
- **服务名**: auth-service
- **基础路径**: `/api/auth`

### 4.1 认证API

#### 用户登录认证
```http
POST /api/auth/login
```

**请求格式**: `application/json`

**请求体**:
```json
{
  "username": "string",
  "password": "string"
}
```

**描述**: 用户身份认证，生成JWT令牌

**响应格式**: `application/json`

**响应示例**:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 86400,
  "tokenType": "Bearer",
  "user": {
    "id": 1,
    "username": "admin",
    "email": "admin@example.com"
  }
}
```

**状态码**:
- `200 OK` - 认证成功
- `401 Unauthorized` - 认证失败

**示例**:
```bash
curl -X POST http://localhost:8083/api/auth/login \
     -H "Content-Type: application/json" \
     -d '{
       "username": "admin",
       "password": "123456"
     }'
```

---

#### 验证JWT令牌
```http
POST /api/auth/validate
```

**请求格式**: `application/json`

**请求体**:
```json
{
  "token": "string"
}
```

**描述**: 验证JWT令牌的有效性

**响应格式**: `application/json`

**响应示例**:
```json
{
  "valid": true,
  "username": "admin",
  "expiresAt": "2024-01-16T15:30:00"
}
```

**状态码**:
- `200 OK` - 令牌有效
- `401 Unauthorized` - 令牌无效或过期

## 5. 系统API

### 健康检查和系统信息

#### 健康检查
```http
GET /api/health
```

**描述**: 检查系统健康状态

**响应格式**: `application/json`

**响应示例**:
```json
{
  "status": "UP",
  "services": {
    "database": "UP",
    "nacos": "UP"
  }
}
```

---

#### 数据库连接测试
```http
GET /api/db-test
```

**描述**: 测试数据库连接状态

**响应格式**: `application/json`

---

#### 服务连通性测试
```http
GET /api/connectivity
```

**描述**: 测试微服务间的连通性

**响应格式**: `application/json`

## 6. 错误响应格式

### 标准错误响应
```json
{
  "error": {
    "code": "ERROR_CODE",
    "message": "错误描述",
    "timestamp": "2024-01-15T15:30:00",
    "path": "/api/path"
  }
}
```

### 常见错误码

| HTTP状态码 | 错误码 | 描述 |
|-----------|--------|------|
| 400 | BAD_REQUEST | 请求参数错误 |
| 401 | UNAUTHORIZED | 未授权访问 |
| 403 | FORBIDDEN | 禁止访问 |
| 404 | NOT_FOUND | 资源不存在 |
| 409 | CONFLICT | 资源冲突 |
| 500 | INTERNAL_ERROR | 服务器内部错误 |

## 7. 认证机制

### Session认证 (Todo Service)
- 使用Spring Session管理用户会话
- Cookie名称: `JSESSIONID`
- 超时时间: 30分钟
- 密码加密: BCrypt

### JWT认证 (Auth Service)
- 使用Bearer Token格式
- 令牌有效期: 24小时
- 请求头格式: `Authorization: Bearer <token>`
- 密钥: 可配置

## 8. 数据库设计

### 用户数据库 (userdb)

#### users表
| 字段 | 类型 | 约束 | 描述 |
|------|------|------|------|
| id | BIGINT | 主键，自增 | 用户ID |
| username | VARCHAR(50) | 唯一，非空 | 用户名 |
| password | VARCHAR(100) | 非空 | BCrypt加密密码 |
| email | VARCHAR(100) | 可空 | 邮箱 |
| phone | VARCHAR(20) | 可空 | 手机号 |
| status | TINYINT | 默认1 | 状态(0-禁用,1-启用) |
| created_at | TIMESTAMP | 自动生成 | 创建时间 |
| updated_at | TIMESTAMP | 自动更新 | 更新时间 |

#### roles表
| 字段 | 类型 | 约束 | 描述 |
|------|------|------|------|
| id | BIGINT | 主键，自增 | 角色ID |
| name | VARCHAR(50) | 唯一，非空 | 角色名称 |
| description | VARCHAR(200) | 可空 | 角色描述 |
| created_at | TIMESTAMP | 自动生成 | 创建时间 |
| updated_at | TIMESTAMP | 自动更新 | 更新时间 |

#### user_roles表
| 字段 | 类型 | 约束 | 描述 |
|------|------|------|------|
| user_id | BIGINT | 外键 | 用户ID |
| role_id | BIGINT | 外键 | 角色ID |
| created_at | TIMESTAMP | 自动生成 | 创建时间 |

### 待办事项数据库 (tododb)

#### todo_items表
| 字段 | 类型 | 约束 | 描述 |
|------|------|------|------|
| id | BIGINT | 主键，自增 | 待办事项ID |
| title | VARCHAR(200) | 非空 | 标题 |
| description | TEXT | 可空 | 描述 |
| completed | BOOLEAN | 默认false | 是否完成 |
| user_id | BIGINT | 非空 | 用户ID |
| created_at | TIMESTAMP | 自动生成 | 创建时间 |
| updated_at | TIMESTAMP | 自动更新 | 更新时间 |

## 9. 测试示例

### 完整流程测试
```bash
# 1. 通过网关访问登录页面
curl -X GET http://localhost:8080/login

# 2. 用户登录
curl -X POST http://localhost:8080/login \
     -H "Content-Type: application/x-www-form-urlencoded" \
     -c cookies.txt \
     -d "username=admin&password=123456"

# 3. 获取待办事项
curl -X GET http://localhost:8080/api/todos \
     -H "Accept: application/json" \
     -b cookies.txt

# 4. 创建待办事项
curl -X POST http://localhost:8080/api/todos \
     -H "Content-Type: application/json" \
     -b cookies.txt \
     -d '{
       "title": "测试任务",
       "description": "通过API测试创建"
     }'

# 5. 获取用户信息
curl -X GET http://localhost:8080/api/users/1 \
     -H "Accept: application/json"

# 6. JWT认证测试
curl -X POST http://localhost:8080/api/auth/login \
     -H "Content-Type: application/json" \
     -d '{
       "username": "admin",
       "password": "123456"
     }'
```

## 10. API限流和安全

### 请求限制
- 登录API: 每分钟最多5次尝试
- 其他API: 每秒最多100次请求

### 安全措施
- 密码使用BCrypt加密
- JWT令牌包含过期时间
- CORS跨域保护
- XSS防护
- CSRF保护 (Session认证)
- 用户数据隔离

## 11. 版本控制

当前API版本: `v1.0-SNAPSHOT`

### 版本兼容策略
- 向后兼容的更改不会增加版本号
- 破坏性更改会创建新版本
- 旧版本支持6个月后废弃

## 12. 服务发现

### Nacos集成
- 所有微服务自动注册到Nacos
- 网关通过服务名进行负载均衡
- 支持健康检查和故障转移
- 动态配置管理 