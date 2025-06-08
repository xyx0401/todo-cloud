# Windows环境下Todo-Cloud项目K3s部署完整指南

## 概述

本指南将详细介绍如何在Windows环境下从零开始部署Todo-Cloud微服务项目到K3s集群。该项目包含4个微服务：Gateway Service、Auth Service、User Service、Todo Service，以及Nacos注册中心和MySQL数据库。

## 前置条件

- Windows 10/11 系统
- 至少8GB内存
- 至少20GB可用磁盘空间
- 管理员权限

## 第一步：环境准备

### 1.1 安装WSL2

1. 以管理员身份打开PowerShell
2. 执行以下命令：
```powershell
# 启用WSL功能
dism.exe /online /enable-feature /featurename:Microsoft-Windows-Subsystem-Linux /all /norestart

# 启用虚拟机平台
dism.exe /online /enable-feature /featurename:VirtualMachinePlatform /all /norestart

# 重启电脑
Restart-Computer
```

3. 重启后安装Ubuntu：
```powershell
# 安装Ubuntu
wsl --install -d Ubuntu

# 设置WSL2为默认版本
wsl --set-default-version 2
```

### 1.2 安装Docker Desktop

1. 从 https://www.docker.com/products/docker-desktop/ 下载Docker Desktop
2. 安装并重启电脑
3. 启动Docker Desktop，确保WSL2集成已启用

### 1.3 安装Java 21

1. 下载Oracle JDK 21或OpenJDK 21
2. 安装并配置环境变量：
```powershell
# 设置JAVA_HOME
[Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Java\jdk-21", "Machine")

# 添加到PATH
$path = [Environment]::GetEnvironmentVariable("PATH", "Machine")
[Environment]::SetEnvironmentVariable("PATH", "$path;%JAVA_HOME%\bin", "Machine")
```

3. 验证安装：
```powershell
java -version
javac -version
```

### 1.4 安装Maven

1. 从 https://maven.apache.org/download.cgi 下载Maven
2. 解压到 `C:\Program Files\Apache\maven`
3. 配置环境变量：
```powershell
# 设置MAVEN_HOME
[Environment]::SetEnvironmentVariable("MAVEN_HOME", "C:\Program Files\Apache\maven", "Machine")

# 添加到PATH
$path = [Environment]::GetEnvironmentVariable("PATH", "Machine")
[Environment]::SetEnvironmentVariable("PATH", "$path;%MAVEN_HOME%\bin", "Machine")
```

4. 验证安装：
```powershell
mvn -version
```

### 1.5 安装kubectl

1. 下载kubectl：
```powershell
curl.exe -LO "https://dl.k8s.io/release/v1.28.0/bin/windows/amd64/kubectl.exe"
```

2. 将kubectl.exe移动到PATH目录或添加到PATH环境变量
3. 验证安装：
```powershell
kubectl version --client
```

## 第二步：项目构建

### 2.1 克隆并编译项目

```powershell
# 进入项目目录
cd /d/作业/分布式系统架构/大作业

# 编译项目
mvn clean package -DskipTests -f todo-cloud/pom.xml
```

### 2.2 创建Docker镜像

为每个服务创建Dockerfile：

**Gateway Service Dockerfile** (`todo-cloud/gateway-service/Dockerfile`):
```dockerfile
FROM openjdk:21-jre-slim
VOLUME /tmp
COPY target/gateway-service-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]
```

**Auth Service Dockerfile** (`todo-cloud/auth-service/Dockerfile`):
```dockerfile
FROM openjdk:21-jre-slim
VOLUME /tmp
COPY target/auth-service-*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]
```

**User Service Dockerfile** (`todo-cloud/user-service/Dockerfile`):
```dockerfile
FROM openjdk:21-jre-slim
VOLUME /tmp
COPY target/user-service-*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]
```

**Todo Service Dockerfile** (`todo-cloud/todo-service/Dockerfile`):
```dockerfile
FROM openjdk:21-jre-slim
VOLUME /tmp
COPY target/todo-service-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]
```

### 2.3 构建Docker镜像

```powershell
# 构建Gateway Service镜像
cd todo-cloud/gateway-service
docker build -t todo-gateway:latest .

# 构建Auth Service镜像
cd ../auth-service
docker build -t todo-auth:latest .

# 构建User Service镜像
cd ../user-service
docker build -t todo-user:latest .

# 构建Todo Service镜像
cd ../todo-service
docker build -t todo-todo:latest .

# 返回根目录
cd ../..
```

## 第三步：K3s安装与配置

### 3.1 安装K3s

在WSL2 Ubuntu中执行：

```bash
# 切换到WSL2
wsl

# 安装K3s
curl -sfL https://get.k3s.io | sh -

# 检查K3s状态
sudo systemctl status k3s

# 获取kubeconfig
sudo cp /etc/rancher/k3s/k3s.yaml ~/.kube/config
sudo chown $USER:$USER ~/.kube/config
```

### 3.2 导入Docker镜像到K3s

```bash
# 保存Docker镜像
docker save todo-gateway:latest | sudo k3s ctr images import -
docker save todo-auth:latest | sudo k3s ctr images import -
docker save todo-user:latest | sudo k3s ctr images import -
docker save todo-todo:latest | sudo k3s ctr images import -

# 验证镜像已导入
sudo k3s ctr images list | grep todo
```

## 第四步：创建Kubernetes部署文件

### 4.1 创建命名空间

创建 `k8s-manifests/namespace.yaml`：
```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: todo-cloud
```

### 4.2 MySQL部署文件

创建 `k8s-manifests/mysql-deployment.yaml`：
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: mysql-initdb
  namespace: todo-cloud
data:
  init.sql: |
    -- 创建数据库
    CREATE DATABASE IF NOT EXISTS tododb DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    CREATE DATABASE IF NOT EXISTS userdb DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    
    -- 使用tododb
    USE tododb;
    CREATE TABLE IF NOT EXISTS todo_items (
        id BIGINT AUTO_INCREMENT PRIMARY KEY,
        title VARCHAR(255) NOT NULL,
        description TEXT,
        completed BOOLEAN DEFAULT FALSE,
        user_id BIGINT NOT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    );
    
    -- 使用userdb
    USE userdb;
    CREATE TABLE IF NOT EXISTS users (
        id BIGINT AUTO_INCREMENT PRIMARY KEY,
        username VARCHAR(50) UNIQUE NOT NULL,
        password VARCHAR(100) NOT NULL,
        email VARCHAR(100),
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );
    
    CREATE TABLE IF NOT EXISTS roles (
        id BIGINT AUTO_INCREMENT PRIMARY KEY,
        name VARCHAR(50) UNIQUE NOT NULL,
        description VARCHAR(200)
    );
    
    CREATE TABLE IF NOT EXISTS user_roles (
        id BIGINT AUTO_INCREMENT PRIMARY KEY,
        user_id BIGINT NOT NULL,
        role_id BIGINT NOT NULL,
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
        FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
        UNIQUE KEY unique_user_role (user_id, role_id)
    );
    
    -- 插入默认角色
    INSERT IGNORE INTO roles (name, description) VALUES 
    ('ADMIN', '管理员角色'),
    ('USER', '普通用户角色');
    
    -- 插入测试用户（密码为BCrypt加密的123456）
    INSERT IGNORE INTO users (username, password, email) VALUES 
    ('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVFrTu', 'admin@example.com'),
    ('user', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVFrTu', 'user@example.com');
    
    -- 分配角色
    INSERT IGNORE INTO user_roles (user_id, role_id) VALUES 
    (1, 1),  -- admin用户分配ADMIN角色
    (1, 2),  -- admin用户同时拥有USER角色
    (2, 2);  -- user用户分配USER角色

---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: mysql-pvc
  namespace: todo-cloud
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 5Gi

---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: mysql
  namespace: todo-cloud
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
          value: "root123456"
        - name: MYSQL_DATABASE
          value: "tododb"
        volumeMounts:
        - name: mysql-storage
          mountPath: /var/lib/mysql
        - name: mysql-initdb
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
        readinessProbe:
          exec:
            command:
            - mysqladmin
            - ping
            - -h
            - localhost
          initialDelaySeconds: 5
          periodSeconds: 5
      volumes:
      - name: mysql-storage
        persistentVolumeClaim:
          claimName: mysql-pvc
      - name: mysql-initdb
        configMap:
          name: mysql-initdb

---
apiVersion: v1
kind: Service
metadata:
  name: mysql
  namespace: todo-cloud
spec:
  selector:
    app: mysql
  ports:
  - port: 3306
    targetPort: 3306
  type: ClusterIP
```

### 4.3 Nacos部署文件

创建 `k8s-manifests/nacos-deployment.yaml`：
```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: nacos-pvc
  namespace: todo-cloud
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 2Gi

---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nacos
  namespace: todo-cloud
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
        image: nacos/nacos-server:v2.2.3
        ports:
        - containerPort: 8848
        - containerPort: 9848
        - containerPort: 9849
        env:
        - name: MODE
          value: "standalone"
        - name: PREFER_HOST_MODE
          value: "hostname"
        - name: SPRING_DATASOURCE_PLATFORM
          value: "mysql"
        - name: MYSQL_SERVICE_HOST
          value: "mysql"
        - name: MYSQL_SERVICE_PORT
          value: "3306"
        - name: MYSQL_SERVICE_DB_NAME
          value: "nacos"
        - name: MYSQL_SERVICE_USER
          value: "root"
        - name: MYSQL_SERVICE_PASSWORD
          value: "root123456"
        volumeMounts:
        - name: nacos-storage
          mountPath: /home/nacos/data
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /nacos/actuator/health
            port: 8848
          initialDelaySeconds: 60
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /nacos/actuator/health
            port: 8848
          initialDelaySeconds: 30
          periodSeconds: 10
      volumes:
      - name: nacos-storage
        persistentVolumeClaim:
          claimName: nacos-pvc

---
apiVersion: v1
kind: Service
metadata:
  name: nacos
  namespace: todo-cloud
spec:
  selector:
    app: nacos
  ports:
  - name: main
    port: 8848
    targetPort: 8848
  - name: client-rpc
    port: 9848
    targetPort: 9848
  - name: raft-rpc
    port: 9849
    targetPort: 9849
  type: ClusterIP
```

### 4.4 Auth Service部署文件

创建 `k8s-manifests/auth-service-deployment.yaml`：
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: auth-service
  namespace: todo-cloud
spec:
  replicas: 2
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
        image: todo-auth:latest
        imagePullPolicy: Never
        ports:
        - containerPort: 8083
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "k8s"
        - name: SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR
          value: "nacos:8848"
        - name: SPRING_DATASOURCE_URL
          value: "jdbc:mysql://mysql:3306/userdb?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
        - name: SPRING_DATASOURCE_USERNAME
          value: "root"
        - name: SPRING_DATASOURCE_PASSWORD
          value: "root123456"
        resources:
          requests:
            memory: "256Mi"
            cpu: "200m"
          limits:
            memory: "512Mi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8083
          initialDelaySeconds: 90
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8083
          initialDelaySeconds: 30
          periodSeconds: 10

---
apiVersion: v1
kind: Service
metadata:
  name: auth-service
  namespace: todo-cloud
spec:
  selector:
    app: auth-service
  ports:
  - port: 8083
    targetPort: 8083
  type: ClusterIP
```

### 4.5 User Service部署文件

创建 `k8s-manifests/user-service-deployment.yaml`：
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: user-service
  namespace: todo-cloud
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
        image: todo-user:latest
        imagePullPolicy: Never
        ports:
        - containerPort: 8082
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "k8s"
        - name: SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR
          value: "nacos:8848"
        - name: SPRING_DATASOURCE_URL
          value: "jdbc:mysql://mysql:3306/userdb?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
        - name: SPRING_DATASOURCE_USERNAME
          value: "root"
        - name: SPRING_DATASOURCE_PASSWORD
          value: "root123456"
        resources:
          requests:
            memory: "256Mi"
            cpu: "200m"
          limits:
            memory: "512Mi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8082
          initialDelaySeconds: 90
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8082
          initialDelaySeconds: 30
          periodSeconds: 10

---
apiVersion: v1
kind: Service
metadata:
  name: user-service
  namespace: todo-cloud
spec:
  selector:
    app: user-service
  ports:
  - port: 8082
    targetPort: 8082
  type: ClusterIP
```

### 4.6 Todo Service部署文件

创建 `k8s-manifests/todo-service-deployment.yaml`：
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: todo-service
  namespace: todo-cloud
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
        image: todo-todo:latest
        imagePullPolicy: Never
        ports:
        - containerPort: 8081
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "k8s"
        - name: SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR
          value: "nacos:8848"
        - name: SPRING_DATASOURCE_URL
          value: "jdbc:mysql://mysql:3306/tododb?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
        - name: SPRING_DATASOURCE_USERNAME
          value: "root"
        - name: SPRING_DATASOURCE_PASSWORD
          value: "root123456"
        resources:
          requests:
            memory: "256Mi"
            cpu: "200m"
          limits:
            memory: "512Mi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8081
          initialDelaySeconds: 90
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8081
          initialDelaySeconds: 30
          periodSeconds: 10

---
apiVersion: v1
kind: Service
metadata:
  name: todo-service
  namespace: todo-cloud
spec:
  selector:
    app: todo-service
  ports:
  - port: 8081
    targetPort: 8081
  type: ClusterIP
```

### 4.7 Gateway Service部署文件

创建 `k8s-manifests/gateway-service-deployment.yaml`：
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: gateway-service
  namespace: todo-cloud
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
        image: todo-gateway:latest
        imagePullPolicy: Never
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "k8s"
        - name: SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR
          value: "nacos:8848"
        resources:
          requests:
            memory: "256Mi"
            cpu: "200m"
          limits:
            memory: "512Mi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 90
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10

---
apiVersion: v1
kind: Service
metadata:
  name: gateway-service
  namespace: todo-cloud
spec:
  selector:
    app: gateway-service
  ports:
  - port: 8080
    targetPort: 8080
    nodePort: 30080
  type: NodePort
```

## 第五步：按序部署应用

### 5.1 创建命名空间
```bash
kubectl apply -f k8s-manifests/namespace.yaml
```

### 5.2 部署MySQL
```bash
kubectl apply -f k8s-manifests/mysql-deployment.yaml

# 等待MySQL就绪
kubectl wait --for=condition=ready pod -l app=mysql -n todo-cloud --timeout=300s
```

### 5.3 部署Nacos
```bash
kubectl apply -f k8s-manifests/nacos-deployment.yaml

# 等待Nacos就绪
kubectl wait --for=condition=ready pod -l app=nacos -n todo-cloud --timeout=300s
```

### 5.4 部署Auth Service
```bash
kubectl apply -f k8s-manifests/auth-service-deployment.yaml

# 等待Auth Service就绪
kubectl wait --for=condition=ready pod -l app=auth-service -n todo-cloud --timeout=300s
```

### 5.5 部署User Service
```bash
kubectl apply -f k8s-manifests/user-service-deployment.yaml

# 等待User Service就绪
kubectl wait --for=condition=ready pod -l app=user-service -n todo-cloud --timeout=300s
```

### 5.6 部署Todo Service
```bash
kubectl apply -f k8s-manifests/todo-service-deployment.yaml

# 等待Todo Service就绪
kubectl wait --for=condition=ready pod -l app=todo-service -n todo-cloud --timeout=300s
```

### 5.7 部署Gateway Service
```bash
kubectl apply -f k8s-manifests/gateway-service-deployment.yaml

# 等待Gateway Service就绪
kubectl wait --for=condition=ready pod -l app=gateway-service -n todo-cloud --timeout=300s
```

## 第六步：验证和测试

### 6.1 检查所有Pod状态
```bash
kubectl get pods -n todo-cloud
```

期望看到所有Pod都处于Running状态。

### 6.2 检查服务状态
```bash
kubectl get services -n todo-cloud
```

### 6.3 查看日志
```bash
# 查看Gateway日志
kubectl logs -f deployment/gateway-service -n todo-cloud

# 查看其他服务日志
kubectl logs -f deployment/auth-service -n todo-cloud
kubectl logs -f deployment/user-service -n todo-cloud
kubectl logs -f deployment/todo-service -n todo-cloud
```

### 6.4 访问应用

通过NodePort访问Gateway：
```bash
# 获取节点IP
kubectl get nodes -o wide

# 访问应用（假设节点IP为192.168.1.100）
curl http://192.168.1.100:30080/actuator/health
```

在Windows中，可以通过浏览器访问：`http://localhost:30080`

### 6.5 测试API

```bash
# 测试登录
curl -X POST http://localhost:30080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}'

# 使用返回的token测试其他API
TOKEN="your_jwt_token_here"

# 获取用户信息
curl -X GET http://localhost:30080/users/me \
  -H "Authorization: Bearer $TOKEN"

# 创建Todo
curl -X POST http://localhost:30080/todos \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"title":"测试任务","description":"这是一个测试任务"}'

# 获取Todo列表
curl -X GET http://localhost:30080/todos \
  -H "Authorization: Bearer $TOKEN"
```

## 第七步：故障排除

### 7.1 常见问题诊断

#### Pod一直处于Pending状态
```bash
# 查看Pod详情
kubectl describe pod <pod-name> -n todo-cloud

# 检查节点资源
kubectl top nodes
```

#### Pod启动失败
```bash
# 查看Pod事件
kubectl describe pod <pod-name> -n todo-cloud

# 查看容器日志
kubectl logs <pod-name> -n todo-cloud

# 进入容器调试
kubectl exec -it <pod-name> -n todo-cloud -- /bin/sh
```

#### 服务无法访问
```bash
# 检查服务配置
kubectl get svc -n todo-cloud

# 检查端点
kubectl get endpoints -n todo-cloud

# 测试服务连通性
kubectl run test-pod --image=busybox -i --tty --rm -- sh
# 在测试Pod中: wget -qO- http://gateway-service:8080/actuator/health
```

#### 数据库连接问题
```bash
# 检查MySQL Pod
kubectl logs -f deployment/mysql -n todo-cloud

# 测试数据库连接
kubectl exec -it deployment/mysql -n todo-cloud -- mysql -uroot -proot123456 -e "SHOW DATABASES;"
```

### 7.2 重新部署单个服务

```bash
# 删除并重新部署特定服务
kubectl delete -f k8s-manifests/auth-service-deployment.yaml
kubectl apply -f k8s-manifests/auth-service-deployment.yaml

# 或者重启Pod
kubectl rollout restart deployment/auth-service -n todo-cloud
```

### 7.3 完全清理和重新部署

```bash
# 删除整个命名空间
kubectl delete namespace todo-cloud

# 重新创建并部署
kubectl apply -f k8s-manifests/namespace.yaml

# 按顺序重新部署所有服务
kubectl apply -f k8s-manifests/mysql-deployment.yaml
# 等待MySQL就绪后继续...
```

## 第八步：维护和监控

### 8.1 查看资源使用情况

```bash
# 查看Pod资源使用
kubectl top pods -n todo-cloud

# 查看节点资源使用
kubectl top nodes
```

### 8.2 扩缩容

```bash
# 扩展Gateway Service副本数
kubectl scale deployment gateway-service --replicas=3 -n todo-cloud

# 缩减Todo Service副本数
kubectl scale deployment todo-service --replicas=1 -n todo-cloud
```

### 8.3 滚动更新

```bash
# 更新镜像
kubectl set image deployment/gateway-service gateway-service=todo-gateway:v2.0 -n todo-cloud

# 查看更新状态
kubectl rollout status deployment/gateway-service -n todo-cloud

# 回滚更新
kubectl rollout undo deployment/gateway-service -n todo-cloud
```

### 8.4 备份和恢复

```bash
# 备份MySQL数据
kubectl exec deployment/mysql -n todo-cloud -- mysqldump -uroot -proot123456 --all-databases > backup.sql

# 恢复数据
kubectl exec -i deployment/mysql -n todo-cloud -- mysql -uroot -proot123456 < backup.sql
```

### 8.5 监控和日志

```bash
# 实时查看所有Pod日志
kubectl logs -f --tail=100 -l app=gateway-service -n todo-cloud

# 查看事件
kubectl get events -n todo-cloud --sort-by='.lastTimestamp'

# 监控Pod状态
watch kubectl get pods -n todo-cloud
```

## 性能优化建议

### 8.6 JVM优化

在部署文件中添加JVM参数：
```yaml
env:
- name: JAVA_OPTS
  value: "-Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

### 8.7 资源限制调优

根据实际使用情况调整资源限制：
```yaml
resources:
  requests:
    memory: "512Mi"
    cpu: "250m"
  limits:
    memory: "1Gi"
    cpu: "500m"
```

## 安全注意事项

1. **密码安全**：在生产环境中，使用Kubernetes Secrets存储敏感信息
2. **网络安全**：配置NetworkPolicy限制Pod间通信
3. **RBAC**：配置适当的角色和权限
4. **镜像安全**：定期更新基础镜像，扫描安全漏洞

## 结语

本指南提供了在Windows环境下完整部署Todo-Cloud项目到K3s集群的详细步骤。通过遵循这些步骤，你应该能够成功部署并运行微服务应用。如果遇到问题，请参考故障排除章节，或查看相关服务的日志进行诊断。

记住，在生产环境中部署时，还需要考虑高可用性、安全性、监控和备份等额外因素。 