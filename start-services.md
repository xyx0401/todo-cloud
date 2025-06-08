# 微服务启动指南

## 前置准备

1. **确保MySQL数据库运行**，并导入init.sql中的数据：
   ```sql
   mysql -u root -p < init.sql
   ```

2. **检查各服务端口是否可用**：
   - gateway-service: 8080
   - todo-service: 8081  
   - user-service: 8082
   - auth-service: 8083

## 启动顺序

### 1. 启动用户服务 (8082端口)

```bash
# 开启第一个终端
cd todo-cloud/user-service
mvn spring-boot:run

# 等待启动完成，看到 "Started UserServiceApplication" 信息
```

### 2. 启动Todo服务 (8081端口)

```bash
# 开启第二个终端  
cd todo-cloud/todo-service
mvn spring-boot:run

# 等待启动完成，看到 "Started TodoServiceApplication" 信息
```

### 3. 启动认证服务 (8083端口)

```bash
# 开启第三个终端
cd todo-cloud/auth-service
mvn spring-boot:run

# 等待启动完成，看到 "Started AuthServiceApplication" 信息
```

### 4. 启动网关服务 (8080端口) - 最后启动

```bash
# 开启第四个终端
cd todo-cloud/gateway-service  
mvn spring-boot:run

# 等待启动完成，看到 "Started GatewayServiceApplication" 信息
```

## 验证服务状态

访问以下URL验证服务是否正常：

- **网关**: http://localhost:8080
- **Todo服务**: http://localhost:8081/api/health
- **用户服务**: http://localhost:8082/api/users/stats  
- **认证服务**: http://localhost:8083/api/auth

## 功能测试

### 1. 登录测试
- 访问：http://localhost:8080/login
- 测试账户：
  - admin / 123456 (管理员)
  - user / 123456 (普通用户)

### 2. API测试
- Todo API: http://localhost:8080/api/todos
- 用户API: http://localhost:8080/api/users
- 系统信息: http://localhost:8080/api/health

### 3. 管理界面测试（需要admin账户）
- 用户管理: http://localhost:8080/admin/users

## 故障排除

### 503错误
- 检查后端服务是否启动
- 检查端口是否冲突
- 查看服务启动日志

### 数据库连接问题
- 检查MySQL是否运行
- 验证数据库配置(用户名/密码)
- 确认数据库和表已创建

### 数据显示问题
- 检查Session中是否有userId
- 确认数据库中有测试数据
- 查看服务日志确认SQL查询

### 用户管理界面问题
- 如果显示"用户服务连接失败"警告，说明user-service(8082)未启动
- 即使user-service未启动，也会显示模拟用户数据用于测试界面
- 确保按顺序启动所有服务

## 快速测试步骤

1. **数据库测试**: 访问 http://localhost:8080/api/db-test
2. **用户服务测试**: 访问 http://localhost:8082/api/users/stats  
3. **Todo服务测试**: 访问 http://localhost:8081/api/todos/stats
4. **网关测试**: 访问 http://localhost:8080/login
5. **用户管理测试**: 登录admin账户后访问 http://localhost:8080/admin/users 