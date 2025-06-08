# K3S 部署方案

## 概述

本文档描述了如何在K3S Kubernetes集群中部署Spring Cloud微服务Todo应用。

## 技术栈

- **Java**: 21 (LTS版本)
- **Spring Boot**: 2.7.18
- **Spring Cloud Alibaba**: 2021.1
- **Nacos**: 1.4.3
- **MySQL**: 8.0
- **K3S**: 轻量级Kubernetes
- **Docker**: 容器化

## 部署架构

```
K3S集群
├── MySQL服务 (mysql-service:3306) - tododb, userdb
├── Nacos服务 (nacos-service:8848) - 服务注册与发现
├── Gateway服务 (gateway-service:8080) - 网关路由
├── Todo服务 (todo-service:8081) - 待办事项和页面
├── User服务 (user-service:8082) - 用户管理
└── Auth服务 (auth-service:8083) - JWT认证
```

## 前置条件

### 1. 安装K3S

```bash
# 安装K3S
curl -sfL https://get.k3s.io | sh -

# 检查K3S状态
sudo systemctl status k3s

# 配置kubectl
sudo cp /etc/rancher/k3s/k3s.yaml ~/.kube/config
sudo chown $USER:$USER ~/.kube/config

# 验证安装
kubectl get nodes
kubectl get pods -A
```

### 2. 创建命名空间

```bash
kubectl create namespace todo-cloud
kubectl config set-context --current --namespace=todo-cloud
```

## 部署配置文件

### 1. MySQL 部署

#### mysql-pv.yaml
```yaml
apiVersion: v1
kind: PersistentVolume
metadata:
  name: mysql-pv
  namespace: todo-cloud
spec:
  capacity:
    storage: 10Gi
  accessModes:
    - ReadWriteOnce
  persistentVolumeReclaimPolicy: Retain
  storageClassName: local-storage
  hostPath:
    path: /opt/mysql-data
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: mysql-pvc
  namespace: todo-cloud
spec:
  accessModes:
    - ReadWriteOnce
  storageClassName: local-storage
  resources:
    requests:
      storage: 10Gi
```

#### mysql-deployment.yaml
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: mysql
  namespace: todo-cloud
  labels:
    app: mysql
spec:
  replicas: 1
  selector:
    matchLabels:
      app: mysql
  template:
    metadata:
      labels:
        app: mysql
    spec:
      containers:
      - name: mysql
        image: mysql:8.0
        ports:
        - containerPort: 3306
        env:
        - name: MYSQL_ROOT_PASSWORD
          value: "123456"
        - name: MYSQL_DATABASE
          value: "tododb"
        volumeMounts:
        - name: mysql-storage
          mountPath: /var/lib/mysql
        - name: mysql-init
          mountPath: /docker-entrypoint-initdb.d
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
        livenessProbe:
          exec:
            command:
            - mysqladmin
            - ping
            - -h
            - localhost
          initialDelaySeconds: 30
          periodSeconds: 10
          timeoutSeconds: 5
        readinessProbe:
          exec:
            command:
            - mysql
            - -h
            - localhost
            - -u
            - root
            - -p123456
            - -e
            - "SELECT 1"
          initialDelaySeconds: 5
          periodSeconds: 2
          timeoutSeconds: 1
      volumes:
      - name: mysql-storage
        persistentVolumeClaim:
          claimName: mysql-pvc
      - name: mysql-init
        configMap:
          name: mysql-init-sql
---
apiVersion: v1
kind: Service
metadata:
  name: mysql-service
  namespace: todo-cloud
spec:
  selector:
    app: mysql
  ports:
  - port: 3306
    targetPort: 3306
  type: ClusterIP
```

#### mysql-init-configmap.yaml
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: mysql-init-sql
  namespace: todo-cloud
data:
  01-init-databases.sql: |
    -- 创建数据库
    CREATE DATABASE IF NOT EXISTS tododb DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    CREATE DATABASE IF NOT EXISTS userdb DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    
    -- 使用tododb
    USE tododb;
    
    -- 创建待办事项表
    CREATE TABLE IF NOT EXISTS todo_items (
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
    
    -- 使用userdb
    USE userdb;
    
    -- 创建用户表
    CREATE TABLE IF NOT EXISTS users (
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
    
    -- 创建角色表
    CREATE TABLE IF NOT EXISTS roles (
        id BIGINT PRIMARY KEY AUTO_INCREMENT,
        name VARCHAR(50) NOT NULL UNIQUE COMMENT '角色名称',
        description VARCHAR(200) COMMENT '角色描述',
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';
    
    -- 创建用户角色关联表
    CREATE TABLE IF NOT EXISTS user_roles (
        user_id BIGINT NOT NULL COMMENT '用户ID',
        role_id BIGINT NOT NULL COMMENT '角色ID',
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
        PRIMARY KEY (user_id, role_id),
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
        FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';
    
    -- 插入初始角色数据
    INSERT IGNORE INTO roles (name, description) VALUES 
    ('ROLE_USER', '普通用户'),
    ('ROLE_ADMIN', '管理员');
    
    -- 插入测试用户数据（密码为123456的BCrypt加密形式）
    INSERT IGNORE INTO users (username, password, email, status) VALUES 
    ('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'admin@example.com', 1),
    ('user', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'user@example.com', 1);
    
    -- 为用户分配角色
    INSERT IGNORE INTO user_roles (user_id, role_id) VALUES 
    (1, 2), -- admin用户分配ROLE_ADMIN角色
    (2, 1); -- user用户分配ROLE_USER角色 
    
    -- 回到tododb数据库添加测试数据
    USE tododb;
    
    -- 插入测试待办事项数据
    INSERT IGNORE INTO todo_items (title, description, completed, user_id) VALUES 
    ('学习Spring Cloud', '学习微服务架构和Spring Cloud组件', false, 1),
    ('完成项目文档', '编写项目的技术文档和使用说明', false, 1),
    ('代码review', '检查和优化现有代码', true, 1),
    ('数据库设计', '设计用户和Todo的数据库表结构', true, 2),
    ('前端页面开发', '开发用户管理和Todo管理页面', false, 2),
    ('API测试', '测试所有REST API接口', false, 2);
```

### 2. Nacos 部署

#### nacos-deployment.yaml
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nacos
  namespace: todo-cloud
  labels:
    app: nacos
spec:
  replicas: 1
  selector:
    matchLabels:
      app: nacos
  template:
    metadata:
      labels:
        app: nacos
    spec:
      containers:
      - name: nacos
        image: nacos/nacos-server:v1.4.3
        ports:
        - containerPort: 8848
        env:
        - name: MODE
          value: "standalone"
        - name: MYSQL_SERVICE_HOST
          value: "mysql-service"
        - name: MYSQL_SERVICE_DB_NAME
          value: "nacos_config"
        - name: MYSQL_SERVICE_PORT
          value: "3306"
        - name: MYSQL_SERVICE_USER
          value: "root"
        - name: MYSQL_SERVICE_PASSWORD
          value: "123456"
        - name: JVM_XMS
          value: "512m"
        - name: JVM_XMX
          value: "512m"
        - name: JVM_XMN
          value: "256m"
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /nacos/v1/console/health/readiness
            port: 8848
          initialDelaySeconds: 60
          periodSeconds: 15
          timeoutSeconds: 5
        readinessProbe:
          httpGet:
            path: /nacos/v1/console/health/readiness
            port: 8848
          initialDelaySeconds: 30
          periodSeconds: 5
          timeoutSeconds: 3
---
apiVersion: v1
kind: Service
metadata:
  name: nacos-service
  namespace: todo-cloud
  labels:
    app: nacos
spec:
  selector:
    app: nacos
  ports:
  - port: 8848
    targetPort: 8848
    nodePort: 30848
  type: NodePort
```

### 3. Gateway Service 部署

#### gateway-deployment.yaml
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: gateway-service
  namespace: todo-cloud
  labels:
    app: gateway-service
spec:
  replicas: 2
  selector:
    matchLabels:
      app: gateway-service
  template:
    metadata:
      labels:
        app: gateway-service
    spec:
      containers:
      - name: gateway-service
        image: todo-cloud/gateway-service:1.0
        ports:
        - containerPort: 8080
        env:
        - name: NACOS_SERVER_ADDR
          value: "nacos-service:8848"
        - name: SPRING_PROFILES_ACTIVE
          value: "k3s"
        - name: JAVA_OPTS
          value: "-Xms256m -Xmx512m -XX:+UseG1GC"
        resources:
          requests:
            memory: "256Mi"
            cpu: "250m"
          limits:
            memory: "512Mi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 15
          timeoutSeconds: 5
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 5
          timeoutSeconds: 3
---
apiVersion: v1
kind: Service
metadata:
  name: gateway-service
  namespace: todo-cloud
  labels:
    app: gateway-service
spec:
  selector:
    app: gateway-service
  ports:
  - port: 8080
    targetPort: 8080
    nodePort: 30080
  type: NodePort
```

### 4. Todo Service 部署

#### todo-service-deployment.yaml
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: todo-service
  namespace: todo-cloud
  labels:
    app: todo-service
spec:
  replicas: 2
  selector:
    matchLabels:
      app: todo-service
  template:
    metadata:
      labels:
        app: todo-service
    spec:
      containers:
      - name: todo-service
        image: todo-cloud/todo-service:1.0
        ports:
        - containerPort: 8081
        env:
        - name: NACOS_SERVER_ADDR
          value: "nacos-service:8848"
        - name: MYSQL_HOST
          value: "mysql-service"
        - name: MYSQL_PORT
          value: "3306"
        - name: MYSQL_USERNAME
          value: "root"
        - name: MYSQL_PASSWORD
          value: "123456"
        - name: SPRING_PROFILES_ACTIVE
          value: "k3s"
        - name: JAVA_OPTS
          value: "-Xms512m -Xmx1g -XX:+UseG1GC"
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8081
          initialDelaySeconds: 60
          periodSeconds: 10
          timeoutSeconds: 5
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8081
          initialDelaySeconds: 90
          periodSeconds: 30
          timeoutSeconds: 10
---
apiVersion: v1
kind: Service
metadata:
  name: todo-service
  namespace: todo-cloud
  labels:
    app: todo-service
spec:
  selector:
    app: todo-service
  ports:
  - port: 8081
    targetPort: 8081
  type: ClusterIP
```

### 5. User Service 部署

#### user-service-deployment.yaml
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: user-service
  namespace: todo-cloud
  labels:
    app: user-service
spec:
  replicas: 2
  selector:
    matchLabels:
      app: user-service
  template:
    metadata:
      labels:
        app: user-service
    spec:
      containers:
      - name: user-service
        image: todo-cloud/user-service:1.0
        ports:
        - containerPort: 8082
        env:
        - name: NACOS_SERVER_ADDR
          value: "nacos-service:8848"
        - name: MYSQL_HOST
          value: "mysql-service"
        - name: MYSQL_PORT
          value: "3306"
        - name: MYSQL_USERNAME
          value: "root"
        - name: MYSQL_PASSWORD
          value: "123456"
        - name: SPRING_PROFILES_ACTIVE
          value: "k3s"
        - name: JAVA_OPTS
          value: "-Xms256m -Xmx512m -XX:+UseG1GC"
        resources:
          requests:
            memory: "256Mi"
            cpu: "250m"
          limits:
            memory: "512Mi"
            cpu: "500m"
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8082
          initialDelaySeconds: 60
          periodSeconds: 10
          timeoutSeconds: 5
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8082
          initialDelaySeconds: 90
          periodSeconds: 30
          timeoutSeconds: 10
---
apiVersion: v1
kind: Service
metadata:
  name: user-service
  namespace: todo-cloud
  labels:
    app: user-service
spec:
  selector:
    app: user-service
  ports:
  - port: 8082
    targetPort: 8082
  type: ClusterIP
```

### 6. Auth Service 部署

#### auth-service-deployment.yaml
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: auth-service
  namespace: todo-cloud
  labels:
    app: auth-service
spec:
  replicas: 1
  selector:
    matchLabels:
      app: auth-service
  template:
    metadata:
      labels:
        app: auth-service
    spec:
      containers:
      - name: auth-service
        image: todo-cloud/auth-service:1.0
        ports:
        - containerPort: 8083
        env:
        - name: NACOS_SERVER_ADDR
          value: "nacos-service:8848"
        - name: SPRING_PROFILES_ACTIVE
          value: "k3s"
        - name: JWT_SECRET
          value: "your-secret-key"
        - name: JAVA_OPTS
          value: "-Xms256m -Xmx512m -XX:+UseG1GC"
        resources:
          requests:
            memory: "256Mi"
            cpu: "250m"
          limits:
            memory: "512Mi"
            cpu: "500m"
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8083
          initialDelaySeconds: 60
          periodSeconds: 10
          timeoutSeconds: 5
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8083
          initialDelaySeconds: 90
          periodSeconds: 30
          timeoutSeconds: 10
---
apiVersion: v1
kind: Service
metadata:
  name: auth-service
  namespace: todo-cloud
  labels:
    app: auth-service
spec:
  selector:
    app: auth-service
  ports:
  - port: 8083
    targetPort: 8083
  type: ClusterIP
```

## 构建Docker镜像

### Dockerfile模板

每个服务的Dockerfile:
```dockerfile
# 使用JDK 21构建镜像
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# 复制maven文件
COPY pom.xml .
COPY ../pom.xml ../

# 复制源代码
COPY src ./src

# 构建应用
RUN ./mvnw clean package -DskipTests

# 运行时镜像
FROM eclipse-temurin:21-jre-alpine

# 安装curl用于健康检查
RUN apk add --no-cache curl

# 创建应用用户
RUN addgroup -g 1000 appuser && adduser -u 1000 -G appuser -s /bin/sh -D appuser

WORKDIR /app

# 复制JAR文件
COPY --from=builder /app/target/*.jar app.jar

# 设置权限
RUN chown appuser:appuser app.jar

# 切换到非root用户
USER appuser

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# 暴露端口
EXPOSE 8080

# JVM优化参数 (Java 21)
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=100"

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar"]
```

### 构建脚本 build-images.sh
```bash
#!/bin/bash

echo "构建Docker镜像 - 使用Java 21和Spring Boot 2.7.18"

# 进入项目目录
cd todo-cloud

# 构建Gateway Service
echo "构建Gateway Service..."
cd gateway-service
docker build -t todo-cloud/gateway-service:1.0 .
cd ..

# 构建Todo Service  
echo "构建Todo Service..."
cd todo-service
docker build -t todo-cloud/todo-service:1.0 .
cd ..

# 构建User Service
echo "构建User Service..."
cd user-service
docker build -t todo-cloud/user-service:1.0 .
cd ..

# 构建Auth Service
echo "构建Auth Service..."
cd auth-service
docker build -t todo-cloud/auth-service:1.0 .
cd ..

echo "所有镜像构建完成！"
docker images | grep todo-cloud
```

## 部署步骤

### 1. 准备环境
```bash
# 创建命名空间
kubectl create namespace todo-cloud

# 设置默认命名空间
kubectl config set-context --current --namespace=todo-cloud

# 创建本地存储目录
sudo mkdir -p /opt/mysql-data
sudo chown -R 999:999 /opt/mysql-data
```

### 2. 构建并导入镜像
```bash
# 进入项目根目录
cd /path/to/your/project

# 构建所有镜像
./build-images.sh

# 将镜像导入K3S (如果使用外部仓库可跳过)
sudo k3s ctr images import gateway-service.tar
sudo k3s ctr images import todo-service.tar
sudo k3s ctr images import user-service.tar
sudo k3s ctr images import auth-service.tar
```

### 3. 部署基础服务
```bash
# 部署MySQL配置
kubectl apply -f mysql-init-configmap.yaml

# 部署MySQL存储
kubectl apply -f mysql-pv.yaml

# 部署MySQL
kubectl apply -f mysql-deployment.yaml

# 等待MySQL启动
kubectl wait --for=condition=ready pod -l app=mysql -n todo-cloud --timeout=300s

# 验证MySQL
kubectl exec -it deployment/mysql -n todo-cloud -- mysql -u root -p123456 -e "SHOW DATABASES;"

# 部署Nacos
kubectl apply -f nacos-deployment.yaml

# 等待Nacos启动
kubectl wait --for=condition=ready pod -l app=nacos -n todo-cloud --timeout=300s
```

### 4. 部署微服务
```bash
# 部署Auth Service
kubectl apply -f auth-service-deployment.yaml

# 部署User Service  
kubectl apply -f user-service-deployment.yaml

# 部署Todo Service
kubectl apply -f todo-service-deployment.yaml

# 部署Gateway Service
kubectl apply -f gateway-deployment.yaml

# 检查所有服务状态
kubectl get pods -n todo-cloud
kubectl get services -n todo-cloud
```

### 5. 验证部署
```bash
# 检查所有Pod状态
kubectl get pods -n todo-cloud -o wide

# 检查服务注册
kubectl port-forward service/nacos-service 8848:8848 -n todo-cloud &
# 访问 http://localhost:8848/nacos 查看服务注册情况

# 检查应用健康状态
kubectl exec -it deployment/gateway-service -n todo-cloud -- curl http://localhost:8080/actuator/health
```

## 访问应用

### 获取访问地址
```bash
# 获取节点IP
kubectl get nodes -o wide

# 获取服务端口
kubectl get services -n todo-cloud

# Gateway访问地址: http://<NODE_IP>:30080
# Nacos控制台: http://<NODE_IP>:30848/nacos (nacos/nacos)
```

### 测试应用功能
```bash
# 获取节点IP
NODE_IP=$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="InternalIP")].address}')

# 测试网关健康检查
curl http://$NODE_IP:30080/actuator/health

# 测试登录页面
curl http://$NODE_IP:30080/login

# 测试API接口
curl -H "Content-Type: application/json" \
     -d '{"username":"admin","password":"123456"}' \
     http://$NODE_IP:30080/api/auth/login
```

## 监控和维护

### 查看服务状态
```bash
# 查看所有Pod状态
kubectl get pods -n todo-cloud -o wide

# 查看服务详情
kubectl describe service gateway-service -n todo-cloud

# 查看Nacos服务注册情况
kubectl port-forward service/nacos-service 8848:8848 -n todo-cloud
```

### 查看日志
```bash
# 查看Gateway日志
kubectl logs -f deployment/gateway-service -n todo-cloud

# 查看Todo Service日志
kubectl logs -f deployment/todo-service -n todo-cloud

# 查看所有服务日志
kubectl logs -f -l app=todo-service -n todo-cloud
```

### 扩缩容
```bash
# 扩展Todo Service实例
kubectl scale deployment todo-service --replicas=3 -n todo-cloud

# 扩展User Service实例
kubectl scale deployment user-service --replicas=3 -n todo-cloud

# 查看扩容状态
kubectl get pods -n todo-cloud -l app=todo-service
```

### 更新部署
```bash
# 更新镜像版本
kubectl set image deployment/todo-service todo-service=todo-cloud/todo-service:1.1 -n todo-cloud

# 查看更新状态
kubectl rollout status deployment/todo-service -n todo-cloud

# 回滚部署
kubectl rollout undo deployment/todo-service -n todo-cloud
```

## 故障排除

### 常见问题

1. **Pod启动失败**
```bash
kubectl describe pod <pod-name> -n todo-cloud
kubectl logs <pod-name> -n todo-cloud --previous
```

2. **服务无法连接**
```bash
kubectl get endpoints -n todo-cloud
kubectl port-forward service/gateway-service 8080:8080 -n todo-cloud
```

3. **数据库连接问题**
```bash
kubectl exec -it deployment/mysql -n todo-cloud -- mysql -u root -p123456
kubectl logs deployment/mysql -n todo-cloud
```

4. **Nacos服务注册问题**
```bash
kubectl logs deployment/nacos -n todo-cloud
kubectl port-forward service/nacos-service 8848:8848 -n todo-cloud
# 访问 http://localhost:8848/nacos 检查服务注册
```

5. **网络连通性问题**
```bash
# 测试Pod间网络连通性
kubectl exec -it deployment/todo-service -n todo-cloud -- wget -qO- http://user-service:8082/actuator/health

# 测试DNS解析
kubectl exec -it deployment/todo-service -n todo-cloud -- nslookup user-service
```

### 性能调优

1. **JVM参数优化**
```bash
# 更新JVM参数
kubectl patch deployment todo-service -n todo-cloud -p='{"spec":{"template":{"spec":{"containers":[{"name":"todo-service","env":[{"name":"JAVA_OPTS","value":"-Xms1g -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=100"}]}]}}}}'
```

2. **资源限制调整**
```bash
# 更新资源限制
kubectl patch deployment todo-service -n todo-cloud -p='{"spec":{"template":{"spec":{"containers":[{"name":"todo-service","resources":{"requests":{"memory":"1Gi","cpu":"500m"},"limits":{"memory":"2Gi","cpu":"1000m"}}}]}}}}'
```

### 清理资源
```bash
# 删除所有服务
kubectl delete namespace todo-cloud

# 清理持久化数据
sudo rm -rf /opt/mysql-data

# 清理Docker镜像
docker rmi $(docker images | grep todo-cloud | awk '{print $3}')
```

## 高可用配置

### MySQL高可用
- 配置MySQL主从复制
- 使用MySQL Operator
- 配置数据备份策略

### Nacos集群配置
- 部署多个Nacos实例
- 配置集群模式
- 使用外部数据库存储配置

### 应用高可用
- 多实例部署
- 健康检查配置
- 自动故障转移

## 安全配置

### 网络策略
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: todo-cloud-netpol
  namespace: todo-cloud
spec:
  podSelector: {}
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          name: todo-cloud
  egress:
  - to:
    - namespaceSelector:
        matchLabels:
          name: todo-cloud
```

### 密钥管理
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: todo-secrets
  namespace: todo-cloud
type: Opaque
data:
  mysql-password: MTIzNDU2  # base64 encoded "123456"
  jwt-secret: eW91ci1zZWNyZXQta2V5  # base64 encoded "your-secret-key"
```

### RBAC配置
```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: todo-service-account
  namespace: todo-cloud
---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  namespace: todo-cloud
  name: todo-role
rules:
- apiGroups: [""]
  resources: ["pods", "services"]
  verbs: ["get", "list", "watch"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: todo-role-binding
  namespace: todo-cloud
subjects:
- kind: ServiceAccount
  name: todo-service-account
  namespace: todo-cloud
roleRef:
  kind: Role
  name: todo-role
  apiGroup: rbac.authorization.k8s.io
```

## 监控集成

### Prometheus监控
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: prometheus-config
  namespace: todo-cloud
data:
  prometheus.yml: |
    global:
      scrape_interval: 15s
    scrape_configs:
    - job_name: 'todo-services'
      kubernetes_sd_configs:
      - role: pod
        namespaces:
          names:
          - todo-cloud
      relabel_configs:
      - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_scrape]
        action: keep
        regex: true
      - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_path]
        action: replace
        target_label: __metrics_path__
        regex: (.+)
```

这个更新的K3S部署方案反映了项目实际的技术栈（Java 21、Spring Boot 2.7.18）和数据库结构，提供了完整的部署、监控和故障排除指南。 