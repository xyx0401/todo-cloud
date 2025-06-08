# CI/CD 管道配置

## 概述

本文档描述了Spring Cloud Todo微服务系统的完整CI/CD管道配置，包括持续集成、持续部署和DevOps最佳实践。

## 技术栈

- **Java**: 21 (LTS版本)
- **Spring Boot**: 2.7.18
- **Spring Cloud Alibaba**: 2021.1
- **Maven**: 构建工具
- **Docker**: 容器化
- **Kubernetes/K3S**: 容器编排
- **GitHub Actions**: CI/CD平台

## 管道架构

```
开发流程
├── 代码提交 → GitHub
├── 自动触发 → GitHub Actions
├── 构建阶段 → Maven Build + Docker Build
├── 测试阶段 → Unit Tests + Integration Tests
├── 质量检查 → SonarQube + Security Scan
├── 制品管理 → Docker Registry
└── 部署阶段 → K3S Deployment
```

## 1. GitHub Actions 工作流

### 1.1 主工作流配置

```yaml
# .github/workflows/ci-cd.yml
name: CI/CD Pipeline

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]
  release:
    types: [ published ]

env:
  REGISTRY: ghcr.io
  IMAGE_NAME: ${{ github.repository }}
  JAVA_VERSION: '21'

jobs:
  # 构建和测试作业
  build-and-test:
    runs-on: ubuntu-latest
    
    services:
      mysql:
        image: mysql:8.0
        env:
          MYSQL_ROOT_PASSWORD: 123456
          MYSQL_DATABASE: tododb
        ports:
          - 3306:3306
        options: --health-cmd="mysqladmin ping" --health-interval=10s --health-timeout=5s --health-retries=3
      
      redis:
        image: redis:6-alpine
        ports:
          - 6379:6379
        options: --health-cmd="redis-cli ping" --health-interval=10s --health-timeout=5s --health-retries=3

    strategy:
      matrix:
        service: [gateway-service, todo-service, user-service, auth-service]

    steps:
    - name: Checkout代码
      uses: actions/checkout@v4

    - name: 设置JDK 8
      uses: actions/setup-java@v4
      with:
        java-version: '8'
        distribution: 'temurin'

    - name: 缓存Maven依赖
      uses: actions/cache@v3
      with:
        path: ~/.m2
        key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}
        restore-keys: ${{ runner.os }}-m2

    - name: 编译${{ matrix.service }}
      run: |
        cd todo-cloud/${{ matrix.service }}
        mvn clean compile

    - name: 运行单元测试
      run: |
        cd todo-cloud/${{ matrix.service }}
        mvn test

    - name: 运行集成测试
      run: |
        cd ${{ matrix.service }}
        mvn verify -Pintegration-test

    - name: 生成测试报告
      uses: dorny/test-reporter@v1
      if: success() || failure()
      with:
        name: ${{ matrix.service }} Test Results
        path: '${{ matrix.service }}/target/surefire-reports/*.xml'
        reporter: java-junit

    - name: 上传覆盖率报告
      uses: codecov/codecov-action@v3
      with:
        file: ${{ matrix.service }}/target/site/jacoco/jacoco.xml
        flags: ${{ matrix.service }}

  # 代码质量检查
  code-quality:
    runs-on: ubuntu-latest
    needs: build-and-test
    
    steps:
    - name: Checkout代码
      uses: actions/checkout@v4
      with:
        fetch-depth: 0

    - name: 设置JDK 11 (SonarQube需要)
      uses: actions/setup-java@v4
      with:
        java-version: '11'
        distribution: 'temurin'

    - name: 缓存SonarQube packages
      uses: actions/cache@v3
      with:
        path: ~/.sonar/cache
        key: ${{ runner.os }}-sonar
        restore-keys: ${{ runner.os }}-sonar

    - name: SonarQube质量检查
      env:
        GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
      run: |
        mvn clean verify sonar:sonar \
          -Dsonar.projectKey=spring-cloud-todo \
          -Dsonar.organization=your-org \
          -Dsonar.host.url=https://sonarcloud.io \
          -Dsonar.login=$SONAR_TOKEN

    - name: 安全扫描
      uses: securecodewarrior/github-action-security-scan@v1
      with:
        token: ${{ secrets.GITHUB_TOKEN }}

  # 构建Docker镜像
  build-images:
    runs-on: ubuntu-latest
    needs: [build-and-test, code-quality]
    if: github.event_name != 'pull_request'
    
    strategy:
      matrix:
        service: [gateway-service, todo-service, user-service, auth-service]

    steps:
    - name: Checkout代码
      uses: actions/checkout@v4

    - name: 设置JDK 8
      uses: actions/setup-java@v4
      with:
        java-version: '8'
        distribution: 'temurin'

    - name: 构建JAR包
      run: |
        cd ${{ matrix.service }}
        mvn clean package -DskipTests

    - name: 登录Container Registry
      uses: docker/login-action@v3
      with:
        registry: ${{ env.REGISTRY }}
        username: ${{ github.actor }}
        password: ${{ secrets.GITHUB_TOKEN }}

    - name: 提取元数据
      id: meta
      uses: docker/metadata-action@v5
      with:
        images: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}/${{ matrix.service }}
        tags: |
          type=ref,event=branch
          type=ref,event=pr
          type=sha
          type=raw,value=latest,enable={{is_default_branch}}

    - name: 构建并推送Docker镜像
      uses: docker/build-push-action@v5
      with:
        context: ${{ matrix.service }}
        push: true
        tags: ${{ steps.meta.outputs.tags }}
        labels: ${{ steps.meta.outputs.labels }}

  # 部署到测试环境
  deploy-staging:
    runs-on: ubuntu-latest
    needs: build-images
    if: github.ref == 'refs/heads/develop'
    environment: staging

    steps:
    - name: Checkout代码
      uses: actions/checkout@v4

    - name: 设置kubectl
      uses: azure/setup-kubectl@v3
      with:
        version: 'v1.21.0'

    - name: 配置kubeconfig
      env:
        KUBE_CONFIG: ${{ secrets.STAGING_KUBE_CONFIG }}
      run: |
        echo "$KUBE_CONFIG" | base64 -d > kubeconfig
        export KUBECONFIG=kubeconfig

    - name: 部署到Staging
      run: |
        # 更新镜像标签
        export IMAGE_TAG=${{ github.sha }}
        envsubst < k8s/staging/deployment.yaml | kubectl apply -f -
        
        # 等待部署完成
        kubectl rollout status deployment/gateway-service -n todo-staging
        kubectl rollout status deployment/todo-service -n todo-staging
        kubectl rollout status deployment/user-service -n todo-staging
        kubectl rollout status deployment/auth-service -n todo-staging

    - name: 运行冒烟测试
      run: |
        # 等待服务就绪
        sleep 60
        
        # 运行基本健康检查
        kubectl exec -n todo-staging deployment/todo-service -- curl -f http://localhost:8081/actuator/health
        
        # 运行API测试
        mvn test -Papi-test -Dtest.endpoint=http://staging.todo-app.com

  # 部署到生产环境
  deploy-production:
    runs-on: ubuntu-latest
    needs: build-images
    if: github.event_name == 'release'
    environment: production

    steps:
    - name: Checkout代码
      uses: actions/checkout@v4

    - name: 设置kubectl
      uses: azure/setup-kubectl@v3
      with:
        version: 'v1.21.0'

    - name: 配置kubeconfig
      env:
        KUBE_CONFIG: ${{ secrets.PROD_KUBE_CONFIG }}
      run: |
        echo "$KUBE_CONFIG" | base64 -d > kubeconfig
        export KUBECONFIG=kubeconfig

    - name: 蓝绿部署
      run: |
        # 部署到绿色环境
        export IMAGE_TAG=${{ github.event.release.tag_name }}
        export ENVIRONMENT=green
        envsubst < k8s/production/deployment.yaml | kubectl apply -f -
        
        # 等待绿色环境就绪
        kubectl rollout status deployment/gateway-service-green -n todo-prod
        kubectl rollout status deployment/todo-service-green -n todo-prod
        kubectl rollout status deployment/user-service-green -n todo-prod
        kubectl rollout status deployment/auth-service-green -n todo-prod
        
        # 运行生产前测试
        ./scripts/production-tests.sh green
        
        # 切换流量到绿色环境
        kubectl patch service gateway-service -n todo-prod -p '{"spec":{"selector":{"version":"green"}}}'
        
        # 验证切换成功
        sleep 30
        ./scripts/verify-deployment.sh
        
        # 清理蓝色环境
        kubectl delete deployment gateway-service-blue -n todo-prod || true
        kubectl delete deployment todo-service-blue -n todo-prod || true
        kubectl delete deployment user-service-blue -n todo-prod || true
        kubectl delete deployment auth-service-blue -n todo-prod || true

  # 性能测试
  performance-test:
    runs-on: ubuntu-latest
    needs: deploy-staging
    if: github.ref == 'refs/heads/develop'

    steps:
    - name: Checkout代码
      uses: actions/checkout@v4

    - name: 设置JMeter
      run: |
        wget https://archive.apache.org/dist/jmeter/binaries/apache-jmeter-5.4.1.tgz
        tar -xzf apache-jmeter-5.4.1.tgz
        export PATH=$PATH:$PWD/apache-jmeter-5.4.1/bin

    - name: 运行性能测试
      run: |
        jmeter -n -t performance-tests/todo-load-test.jmx \
               -l performance-results.jtl \
               -Jserver.host=staging.todo-app.com \
               -Jserver.port=80 \
               -Jusers=100 \
               -Jrampup=300 \
               -Jduration=600

    - name: 分析性能结果
      run: |
        # 生成HTML报告
        jmeter -g performance-results.jtl -o performance-report/
        
        # 检查性能阈值
        python scripts/check-performance.py performance-results.jtl

    - name: 上传性能报告
      uses: actions/upload-artifact@v3
      with:
        name: performance-report
        path: performance-report/
```

### 1.2 特性分支工作流

```yaml
# .github/workflows/feature-branch.yml
name: Feature Branch CI

on:
  push:
    branches-ignore: [main, develop]
  pull_request:
    types: [opened, synchronize, reopened]

jobs:
  validate-feature:
    runs-on: ubuntu-latest
    
    steps:
    - name: Checkout代码
      uses: actions/checkout@v4

    - name: 设置JDK 8
      uses: actions/setup-java@v4
      with:
        java-version: '8'
        distribution: 'temurin'

    - name: 验证代码风格
      run: |
        mvn spotless:check

    - name: 编译检查
      run: |
        mvn clean compile

    - name: 运行快速测试
      run: |
        mvn test -Dtest.groups=unit

    - name: 安全漏洞扫描
      run: |
        mvn dependency-check:check

  code-review:
    runs-on: ubuntu-latest
    if: github.event_name == 'pull_request'
    
    steps:
    - name: Checkout代码
      uses: actions/checkout@v4

    - name: PR质量检查
      uses: github/super-linter@v4
      env:
        DEFAULT_BRANCH: main
        GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        VALIDATE_JAVA: true
        VALIDATE_DOCKERFILE: true
        VALIDATE_YAML: true

    - name: 代码复杂度分析
      run: |
        mvn pmd:check
        mvn spotbugs:check
```

## 2. Docker 配置

### 2.1 多阶段构建 Dockerfile

```dockerfile
# 通用服务 Dockerfile 模板
FROM maven:3.8.4-openjdk-8-slim AS builder

WORKDIR /app

# 复制pom文件并下载依赖（利用Docker缓存）
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 复制源代码并构建
COPY src ./src
RUN mvn clean package -DskipTests

# 运行时镜像
FROM openjdk:8-jre-slim

# 安装curl用于健康检查
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# 创建应用用户
RUN groupadd -r appuser && useradd -r -g appuser appuser

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

# JVM优化参数
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:+PrintGCDetails -XX:+PrintGCTimeStamps"

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar"]
```

### 2.2 Docker Compose 开发环境

```yaml
# docker-compose.dev.yml
version: '3.8'

services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: 123456
      MYSQL_DATABASE: tododb
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql
      - ./scripts/init-db.sql:/docker-entrypoint-initdb.d/init-db.sql
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 30s
      timeout: 10s
      retries: 5

  redis:
    image: redis:6-alpine
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 30s
      timeout: 10s
      retries: 5

  nacos:
    image: nacos/nacos-server:v1.4.3
    environment:
      MODE: standalone
      SPRING_DATASOURCE_PLATFORM: mysql
      MYSQL_SERVICE_HOST: mysql
      MYSQL_SERVICE_DB_NAME: nacos_config
      MYSQL_SERVICE_PORT: 3306
      MYSQL_SERVICE_USER: root
      MYSQL_SERVICE_PASSWORD: 123456
    ports:
      - "8848:8848"
    depends_on:
      mysql:
        condition: service_healthy

  gateway-service:
    build: 
      context: ./gateway-service
      dockerfile: Dockerfile.dev
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: dev
      NACOS_SERVER_ADDR: nacos:8848
    depends_on:
      - nacos
    volumes:
      - ./gateway-service/src:/app/src
      - ./gateway-service/target:/app/target

  todo-service:
    build: 
      context: ./todo-service
      dockerfile: Dockerfile.dev
    ports:
      - "8081:8081"
    environment:
      SPRING_PROFILES_ACTIVE: dev
      NACOS_SERVER_ADDR: nacos:8848
      MYSQL_HOST: mysql
      MYSQL_PORT: 3306
      MYSQL_USERNAME: root
      MYSQL_PASSWORD: 123456
    depends_on:
      mysql:
        condition: service_healthy
      nacos:
        condition: service_started
    volumes:
      - ./todo-service/src:/app/src
      - ./todo-service/target:/app/target

  user-service:
    build: 
      context: ./user-service
      dockerfile: Dockerfile.dev
    ports:
      - "8082:8082"
    environment:
      SPRING_PROFILES_ACTIVE: dev
      NACOS_SERVER_ADDR: nacos:8848
      MYSQL_HOST: mysql
      MYSQL_PORT: 3306
      MYSQL_USERNAME: root
      MYSQL_PASSWORD: 123456
    depends_on:
      mysql:
        condition: service_healthy
      nacos:
        condition: service_started

  auth-service:
    build: 
      context: ./auth-service
      dockerfile: Dockerfile.dev
    ports:
      - "8083:8083"
    environment:
      SPRING_PROFILES_ACTIVE: dev
      NACOS_SERVER_ADDR: nacos:8848
    depends_on:
      - nacos

volumes:
  mysql_data:
```

## 3. Kubernetes 部署配置

### 3.1 生产环境部署配置

```yaml
# k8s/production/namespace.yaml
apiVersion: v1
kind: Namespace
metadata:
  name: todo-prod
  labels:
    environment: production
    app: todo-cloud

---
# k8s/production/configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: todo-config
  namespace: todo-prod
data:
  nacos.server.addr: "nacos-service:8848"
  mysql.host: "mysql-service"
  mysql.port: "3306"
  mysql.database: "tododb"
  spring.profiles.active: "prod"

---
# k8s/production/secret.yaml
apiVersion: v1
kind: Secret
metadata:
  name: todo-secrets
  namespace: todo-prod
type: Opaque
data:
  mysql.username: cm9vdA==  # root
  mysql.password: MTIzNDU2  # 123456
  jwt.secret: bXlzZWNyZXRrZXk=  # mysecretkey

---
# k8s/production/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: gateway-service-${ENVIRONMENT:-blue}
  namespace: todo-prod
  labels:
    app: gateway-service
    version: ${ENVIRONMENT:-blue}
spec:
  replicas: 3
  selector:
    matchLabels:
      app: gateway-service
      version: ${ENVIRONMENT:-blue}
  template:
    metadata:
      labels:
        app: gateway-service
        version: ${ENVIRONMENT:-blue}
    spec:
      containers:
      - name: gateway-service
        image: ghcr.io/your-org/todo-cloud/gateway-service:${IMAGE_TAG}
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          valueFrom:
            configMapKeyRef:
              name: todo-config
              key: spring.profiles.active
        - name: NACOS_SERVER_ADDR
          valueFrom:
            configMapKeyRef:
              name: todo-config
              key: nacos.server.addr
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
          initialDelaySeconds: 90
          periodSeconds: 30
          timeoutSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 10
          timeoutSeconds: 5
        securityContext:
          runAsNonRoot: true
          runAsUser: 1000
          allowPrivilegeEscalation: false

---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: todo-service-${ENVIRONMENT:-blue}
  namespace: todo-prod
  labels:
    app: todo-service
    version: ${ENVIRONMENT:-blue}
spec:
  replicas: 4
  selector:
    matchLabels:
      app: todo-service
      version: ${ENVIRONMENT:-blue}
  template:
    metadata:
      labels:
        app: todo-service
        version: ${ENVIRONMENT:-blue}
    spec:
      containers:
      - name: todo-service
        image: ghcr.io/your-org/todo-cloud/todo-service:${IMAGE_TAG}
        ports:
        - containerPort: 8081
        env:
        - name: SPRING_PROFILES_ACTIVE
          valueFrom:
            configMapKeyRef:
              name: todo-config
              key: spring.profiles.active
        - name: NACOS_SERVER_ADDR
          valueFrom:
            configMapKeyRef:
              name: todo-config
              key: nacos.server.addr
        - name: MYSQL_HOST
          valueFrom:
            configMapKeyRef:
              name: todo-config
              key: mysql.host
        - name: MYSQL_PORT
          valueFrom:
            configMapKeyRef:
              name: todo-config
              key: mysql.port
        - name: MYSQL_USERNAME
          valueFrom:
            secretKeyRef:
              name: todo-secrets
              key: mysql.username
        - name: MYSQL_PASSWORD
          valueFrom:
            secretKeyRef:
              name: todo-secrets
              key: mysql.password
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8081
          initialDelaySeconds: 120
          periodSeconds: 30
          timeoutSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8081
          initialDelaySeconds: 90
          periodSeconds: 10
          timeoutSeconds: 5

---
# Service配置
apiVersion: v1
kind: Service
metadata:
  name: gateway-service
  namespace: todo-prod
  labels:
    app: gateway-service
spec:
  selector:
    app: gateway-service
    version: blue  # 默认指向蓝色环境
  ports:
  - port: 8080
    targetPort: 8080
    protocol: TCP
  type: ClusterIP

---
apiVersion: v1
kind: Service
metadata:
  name: todo-service
  namespace: todo-prod
  labels:
    app: todo-service
spec:
  selector:
    app: todo-service
    version: blue  # 默认指向蓝色环境
  ports:
  - port: 8081
    targetPort: 8081
    protocol: TCP
  type: ClusterIP

---
# Ingress配置
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: todo-ingress
  namespace: todo-prod
  annotations:
    kubernetes.io/ingress.class: nginx
    cert-manager.io/cluster-issuer: letsencrypt-prod
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    nginx.ingress.kubernetes.io/force-ssl-redirect: "true"
spec:
  tls:
  - hosts:
    - todo-app.com
    secretName: todo-tls
  rules:
  - host: todo-app.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: gateway-service
            port:
              number: 8080

---
# HPA配置
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: todo-service-hpa
  namespace: todo-prod
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: todo-service-blue
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

## 4. 部署脚本

### 4.1 自动化部署脚本

```bash
#!/bin/bash
# scripts/deploy.sh

set -e

# 配置变量
ENVIRONMENT=${1:-staging}
IMAGE_TAG=${2:-latest}
NAMESPACE="todo-${ENVIRONMENT}"

echo "开始部署到环境: $ENVIRONMENT"
echo "镜像标签: $IMAGE_TAG"

# 检查kubectl连接
if ! kubectl cluster-info > /dev/null 2>&1; then
    echo "错误: 无法连接到Kubernetes集群"
    exit 1
fi

# 创建命名空间（如果不存在）
kubectl create namespace $NAMESPACE --dry-run=client -o yaml | kubectl apply -f -

# 部署基础设施组件
echo "部署基础设施..."
kubectl apply -f k8s/$ENVIRONMENT/infrastructure/ -n $NAMESPACE

# 等待基础设施就绪
echo "等待基础设施就绪..."
kubectl wait --for=condition=ready pod -l app=mysql -n $NAMESPACE --timeout=300s
kubectl wait --for=condition=ready pod -l app=nacos -n $NAMESPACE --timeout=300s

# 部署应用服务
echo "部署应用服务..."
export IMAGE_TAG
export ENVIRONMENT
envsubst < k8s/$ENVIRONMENT/deployment.yaml | kubectl apply -f -

# 等待部署完成
echo "等待部署完成..."
kubectl rollout status deployment/gateway-service -n $NAMESPACE --timeout=600s
kubectl rollout status deployment/todo-service -n $NAMESPACE --timeout=600s
kubectl rollout status deployment/user-service -n $NAMESPACE --timeout=600s
kubectl rollout status deployment/auth-service -n $NAMESPACE --timeout=600s

# 运行部署后测试
echo "运行部署后测试..."
./scripts/post-deploy-test.sh $ENVIRONMENT

echo "部署完成!"
```

### 4.2 蓝绿部署脚本

```bash
#!/bin/bash
# scripts/blue-green-deploy.sh

set -e

NAMESPACE="todo-prod"
NEW_VERSION=${1:-green}
IMAGE_TAG=${2:-latest}

# 确定当前和新版本
if [ "$NEW_VERSION" = "green" ]; then
    CURRENT_VERSION="blue"
    NEW_VERSION="green"
else
    CURRENT_VERSION="green"
    NEW_VERSION="blue"
fi

echo "当前版本: $CURRENT_VERSION"
echo "新版本: $NEW_VERSION"

# 部署新版本
echo "部署新版本..."
export ENVIRONMENT=$NEW_VERSION
export IMAGE_TAG
envsubst < k8s/production/deployment.yaml | kubectl apply -f -

# 等待新版本就绪
echo "等待新版本就绪..."
kubectl rollout status deployment/gateway-service-$NEW_VERSION -n $NAMESPACE --timeout=600s
kubectl rollout status deployment/todo-service-$NEW_VERSION -n $NAMESPACE --timeout=600s

# 运行预生产测试
echo "运行预生产测试..."
./scripts/pre-production-test.sh $NEW_VERSION

# 切换流量
echo "切换流量到新版本..."
kubectl patch service gateway-service -n $NAMESPACE -p "{\"spec\":{\"selector\":{\"version\":\"$NEW_VERSION\"}}}"
kubectl patch service todo-service -n $NAMESPACE -p "{\"spec\":{\"selector\":{\"version\":\"$NEW_VERSION\"}}}"

# 验证切换
echo "验证流量切换..."
sleep 30
./scripts/verify-deployment.sh

# 等待确认
echo "新版本部署成功，等待60秒后清理旧版本..."
sleep 60

# 清理旧版本
echo "清理旧版本..."
kubectl delete deployment gateway-service-$CURRENT_VERSION -n $NAMESPACE || true
kubectl delete deployment todo-service-$CURRENT_VERSION -n $NAMESPACE || true
kubectl delete deployment user-service-$CURRENT_VERSION -n $NAMESPACE || true
kubectl delete deployment auth-service-$CURRENT_VERSION -n $NAMESPACE || true

echo "蓝绿部署完成!"
```

### 4.3 回滚脚本

```bash
#!/bin/bash
# scripts/rollback.sh

set -e

NAMESPACE="todo-prod"
SERVICE=${1:-all}

echo "开始回滚服务: $SERVICE"

if [ "$SERVICE" = "all" ]; then
    # 回滚所有服务
    kubectl rollout undo deployment/gateway-service -n $NAMESPACE
    kubectl rollout undo deployment/todo-service -n $NAMESPACE
    kubectl rollout undo deployment/user-service -n $NAMESPACE
    kubectl rollout undo deployment/auth-service -n $NAMESPACE
else
    # 回滚指定服务
    kubectl rollout undo deployment/$SERVICE -n $NAMESPACE
fi

# 等待回滚完成
echo "等待回滚完成..."
if [ "$SERVICE" = "all" ]; then
    kubectl rollout status deployment/gateway-service -n $NAMESPACE
    kubectl rollout status deployment/todo-service -n $NAMESPACE
    kubectl rollout status deployment/user-service -n $NAMESPACE
    kubectl rollout status deployment/auth-service -n $NAMESPACE
else
    kubectl rollout status deployment/$SERVICE -n $NAMESPACE
fi

# 验证回滚
echo "验证回滚结果..."
./scripts/verify-deployment.sh

echo "回滚完成!"
```

## 5. 监控和日志

### 5.1 Prometheus 监控配置

```yaml
# k8s/monitoring/prometheus-config.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: prometheus-config
  namespace: monitoring
data:
  prometheus.yml: |
    global:
      scrape_interval: 15s
      evaluation_interval: 15s

    rule_files:
      - "alerts.yml"

    scrape_configs:
      - job_name: 'kubernetes-pods'
        kubernetes_sd_configs:
        - role: pod
        relabel_configs:
        - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_scrape]
          action: keep
          regex: true
        - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_path]
          action: replace
          target_label: __metrics_path__
          regex: (.+)
        - source_labels: [__address__, __meta_kubernetes_pod_annotation_prometheus_io_port]
          action: replace
          regex: ([^:]+)(?::\d+)?;(\d+)
          replacement: $1:$2
          target_label: __address__

      - job_name: 'todo-services'
        static_configs:
        - targets: 
          - 'gateway-service:8080'
          - 'todo-service:8081'
          - 'user-service:8082'
          - 'auth-service:8083'
        metrics_path: '/actuator/prometheus'

    alerting:
      alertmanagers:
      - static_configs:
        - targets:
          - alertmanager:9093

  alerts.yml: |
    groups:
    - name: todo-alerts
      rules:
      - alert: ServiceDown
        expr: up == 0
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "服务 {{ $labels.job }} 已停止"
          description: "服务 {{ $labels.job }} 在实例 {{ $labels.instance }} 上已停止超过5分钟"

      - alert: HighCPUUsage
        expr: cpu_usage_percent > 80
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "高CPU使用率"
          description: "实例 {{ $labels.instance }} CPU使用率超过80%"

      - alert: HighMemoryUsage
        expr: memory_usage_percent > 85
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "高内存使用率"
          description: "实例 {{ $labels.instance }} 内存使用率超过85%"

      - alert: DatabaseConnectionError
        expr: database_connections_failed_total > 0
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "数据库连接失败"
          description: "服务无法连接到数据库"
```

### 5.2 Grafana Dashboard 配置

```json
{
  "dashboard": {
    "id": null,
    "title": "Todo Cloud 微服务监控",
    "tags": ["todo", "microservices"],
    "timezone": "browser",
    "panels": [
      {
        "id": 1,
        "title": "服务状态",
        "type": "stat",
        "targets": [
          {
            "expr": "up{job=~\".*-service\"}",
            "legendFormat": "{{job}}"
          }
        ],
        "fieldConfig": {
          "defaults": {
            "color": {
              "mode": "thresholds"
            },
            "thresholds": {
              "steps": [
                {"color": "red", "value": 0},
                {"color": "green", "value": 1}
              ]
            }
          }
        }
      },
      {
        "id": 2,
        "title": "请求速率",
        "type": "graph",
        "targets": [
          {
            "expr": "rate(http_requests_total[5m])",
            "legendFormat": "{{job}} - {{method}} {{status}}"
          }
        ]
      },
      {
        "id": 3,
        "title": "响应时间",
        "type": "graph",
        "targets": [
          {
            "expr": "histogram_quantile(0.95, rate(http_request_duration_seconds_bucket[5m]))",
            "legendFormat": "95th percentile"
          },
          {
            "expr": "histogram_quantile(0.50, rate(http_request_duration_seconds_bucket[5m]))",
            "legendFormat": "50th percentile"
          }
        ]
      }
    ],
    "time": {
      "from": "now-1h",
      "to": "now"
    },
    "refresh": "30s"
  }
}
```

## 6. 环境管理

### 6.1 环境配置

```yaml
# environments/staging.env
ENVIRONMENT=staging
REPLICAS=1
CPU_REQUEST=250m
CPU_LIMIT=500m
MEMORY_REQUEST=256Mi
MEMORY_LIMIT=512Mi
MYSQL_POOL_SIZE=5
LOG_LEVEL=DEBUG

# environments/production.env
ENVIRONMENT=production
REPLICAS=3
CPU_REQUEST=500m
CPU_LIMIT=1000m
MEMORY_REQUEST=512Mi
MEMORY_LIMIT=1Gi
MYSQL_POOL_SIZE=20
LOG_LEVEL=INFO
```

### 6.2 配置管理脚本

```bash
#!/bin/bash
# scripts/manage-config.sh

ENVIRONMENT=${1:-staging}
ACTION=${2:-apply}

case $ACTION in
    "apply")
        echo "应用 $ENVIRONMENT 环境配置..."
        kubectl create configmap app-config \
            --from-env-file=environments/$ENVIRONMENT.env \
            --namespace=todo-$ENVIRONMENT \
            --dry-run=client -o yaml | kubectl apply -f -
        ;;
    "backup")
        echo "备份 $ENVIRONMENT 环境配置..."
        kubectl get configmap app-config \
            --namespace=todo-$ENVIRONMENT \
            -o yaml > backups/config-$ENVIRONMENT-$(date +%Y%m%d).yaml
        ;;
    "restore")
        BACKUP_FILE=${3:-backups/config-$ENVIRONMENT-latest.yaml}
        echo "恢复 $ENVIRONMENT 环境配置..."
        kubectl apply -f $BACKUP_FILE
        ;;
esac
```

## 7. 安全配置

### 7.1 网络策略

```yaml
# k8s/security/network-policy.yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: todo-network-policy
  namespace: todo-prod
spec:
  podSelector: {}
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          name: todo-prod
    - namespaceSelector:
        matchLabels:
          name: ingress-nginx
  egress:
  - to:
    - namespaceSelector:
        matchLabels:
          name: todo-prod
  - to: []
    ports:
    - protocol: TCP
      port: 53
    - protocol: UDP
      port: 53
    - protocol: TCP
      port: 443
```

### 7.2 Pod 安全策略

```yaml
# k8s/security/pod-security-policy.yaml
apiVersion: policy/v1beta1
kind: PodSecurityPolicy
metadata:
  name: todo-psp
spec:
  privileged: false
  allowPrivilegeEscalation: false
  requiredDropCapabilities:
    - ALL
  volumes:
    - 'configMap'
    - 'emptyDir'
    - 'projected'
    - 'secret'
    - 'downwardAPI'
    - 'persistentVolumeClaim'
  runAsUser:
    rule: 'MustRunAsNonRoot'
  seLinux:
    rule: 'RunAsAny'
  fsGroup:
    rule: 'RunAsAny'
```

## 8. 维护脚本

### 8.1 数据库维护

```bash
#!/bin/bash
# scripts/db-maintenance.sh

ENVIRONMENT=${1:-staging}
ACTION=${2:-backup}

case $ACTION in
    "backup")
        echo "备份数据库..."
        kubectl exec -n todo-$ENVIRONMENT deployment/mysql -- \
            mysqldump -u root -p123456 tododb > backups/db-$ENVIRONMENT-$(date +%Y%m%d).sql
        ;;
    "restore")
        BACKUP_FILE=${3:-backups/db-$ENVIRONMENT-latest.sql}
        echo "恢复数据库..."
        kubectl exec -i -n todo-$ENVIRONMENT deployment/mysql -- \
            mysql -u root -p123456 tododb < $BACKUP_FILE
        ;;
    "migrate")
        echo "执行数据库迁移..."
        kubectl exec -n todo-$ENVIRONMENT deployment/todo-service -- \
            java -jar app.jar --spring.profiles.active=migrate
        ;;
esac
```

### 8.2 清理脚本

```bash
#!/bin/bash
# scripts/cleanup.sh

ENVIRONMENT=${1:-staging}
DAYS=${2:-7}

echo "清理 $ENVIRONMENT 环境中 $DAYS 天前的资源..."

# 清理旧的部署
kubectl get deployment -n todo-$ENVIRONMENT --sort-by=.metadata.creationTimestamp | \
    head -n -3 | tail -n +2 | awk '{print $1}' | \
    xargs -I {} kubectl delete deployment {} -n todo-$ENVIRONMENT

# 清理旧的镜像
docker images --format "table {{.Repository}}\t{{.Tag}}\t{{.CreatedAt}}" | \
    grep "todo-cloud" | grep "$DAYS days ago" | awk '{print $1":"$2}' | \
    xargs -I {} docker rmi {}

echo "清理完成!"
```

这个CI/CD管道配置提供了完整的自动化部署流程，包括构建、测试、部署、监控和维护等各个环节，确保了应用的高质量交付和稳定运行。 