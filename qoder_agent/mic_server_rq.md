# DataAgent 微服务架构改造需求文档

## 📋 文档概述

本文档详细阐述了将 DataAgent 项目从**单体应用**改造为**微服务架构**的技术需求与实施方案。项目将引入 **Consul** 作为服务注册中心和配置中心，实现服务的自动发现、动态配置管理和负载均衡。

**改造目标**：确保微服务架构可用，支持服务横向扩展，提升系统可维护性和可靠性。

---

## 一、背景与目标

### 1.1 当前架构现状

DataAgent 当前采用**单体应用架构**：

| 组件类型   | 实现方式                   | 端口 | 说明                     |
| ---------- | -------------------------- | ---- | ------------------------ |
| **前端**   | Vue.js 3 单页应用          | 3000 | 独立部署的前端应用       |
| **后端**   | Spring Boot 3.4.8 单体应用 | 8065 | 所有业务逻辑在一个应用中 |
| **数据库** | MySQL 5.7+                 | 3306 | 元数据存储               |

**单体架构的局限性**：
- ❌ **扩展性差**：无法针对特定模块独立扩容
- ❌ **耦合度高**：所有功能模块紧密耦合，修改影响面大
- ❌ **部署风险**：任何修改都需要整体重新部署
- ❌ **故障影响**：单点故障导致整个系统不可用
- ❌ **技术栈固化**：所有模块必须使用相同的技术栈

### 1.2 改造目标

**业务目标**：
1. 支持业务模块独立部署和扩展
2. 降低系统耦合度，提升可维护性
3. 提高系统可用性和容错能力
4. 为未来支持多租户、多区域部署打基础

**技术目标**：
1. 引入 Consul 作为服务注册中心和配置中心
2. 拆分单体应用为多个微服务模块
3. 实现服务间通过 REST API 和 gRPC 通信
4. 支持服务动态扩缩容和负载均衡
5. 实现配置的集中管理和动态刷新
6. 保持前端应用不变，通过 API 网关统一访问

### 1.3 设计原则

- **渐进式改造**：先拆分核心模块，逐步完善
- **向后兼容**：保证现有功能不受影响
- **开发友好**：本地开发环境配置简单
- **生产可用**：改造后系统稳定可靠
- **易于运维**：提供完整的监控和日志方案

---

## 二、目标架构设计

### 2.1 微服务拆分方案

根据 DataAgent 的业务功能，拆分为以下微服务：

```
┌─────────────────────────────────────────────────────────────┐
│                      前端应用 (Vue.js)                        │
│                    http://localhost:3000                     │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        ↓
┌─────────────────────────────────────────────────────────────┐
│                   API 网关 (Spring Cloud Gateway)             │
│                    http://localhost:8080                     │
│  功能: 路由转发、负载均衡、鉴权、限流                          │
└───────────┬────────────┬────────────┬───────────────────────┘
            │            │            │
    ┌───────┴───┐   ┌────┴────┐   ┌──┴─────┐
    ↓           ↓   ↓         ↓   ↓        ↓
┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐
│ Agent  │ │DataSrce│ │ Chat   │ │ Model  │
│Service │ │Service │ │Service │ │Service │
│ :8081  │ │ :8082  │ │ :8083  │ │ :8084  │
└───┬────┘ └───┬────┘ └───┬────┘ └───┬────┘
    │          │          │          │
    └──────────┴──────────┴──────────┘
                    │
                    ↓
          ┌──────────────────┐
          │  Consul Cluster   │
          │  :8500 (HTTP)     │
          │  :8600 (DNS)      │
          │  功能:             │
          │  - 服务注册与发现  │
          │  - 配置管理        │
          │  - 健康检查        │
          └──────────────────┘
                    │
                    ↓
          ┌──────────────────┐
          │   MySQL 集群      │
          │   :3306           │
          └──────────────────┘
```

### 2.2 微服务模块划分

#### 2.2.1 API 网关服务 (Gateway Service)

**模块名称**：`data-agent-gateway`

**职责**：
- 统一入口，接收前端所有 HTTP 请求
- 路由转发到对应的后端微服务
- 负载均衡（Consul 服务发现）
- 统一鉴权和权限控制
- 请求限流和熔断降级
- 跨域处理（CORS）

**端口**：`8080`

**技术栈**：
- Spring Cloud Gateway 4.1.x
- Spring Boot 3.4.8
- Spring Cloud Consul Discovery

#### 2.2.2 Agent 管理服务 (Agent Service)

**模块名称**：`data-agent-service`

**职责**：
- Agent 的 CRUD 操作
- Agent 配置管理（提示词、预设问题等）
- Agent 与数据源的关联管理
- Agent 访问密钥管理

**端口**：`8081`

**数据库表**：
- `agent`
- `agent_datasource`
- `agent_knowledge`
- `preset_question`
- `model_config`

#### 2.2.3 数据源管理服务 (DataSource Service)

**模块名称**：`data-agent-datasource-service`

**职责**：
- 数据源的 CRUD 操作
- 数据源连接测试
- 数据库元数据获取（表、字段、索引）
- 数据源连接池管理
- 支持多种数据库（MySQL、PostgreSQL、达梦、GaussDB、DWS）

**端口**：`8082`

**数据库表**：
- `datasource`
- `datasource_connector`

#### 2.2.4 对话服务 (Chat Service)

**模块名称**：`data-agent-chat-service`

**职责**：
- 用户对话管理（会话创建、历史记录）
- 自然语言查询处理（NL2SQL）
- 调用大模型服务
- 执行 SQL 查询
- Python 代码执行（可选）
- 智能报告生成

**端口**：`8083`

**数据库表**：
- `chat_session`
- `chat_message`
- `query_history`

#### 2.2.5 模型配置服务 (Model Service)

**模块名称**：`data-agent-model-service`

**职责**：
- 大模型配置管理（OpenAI、通义千问等）
- 向量数据库配置（Elasticsearch、Simple）
- RAG 配置（文档上传、向量化、检索）
- 业务知识库管理

**端口**：`8084`

**数据库表**：
- `business_knowledge`
- `logical_relation`
- `graph_config`

### 2.3 服务间通信方案

#### 2.3.1 同步通信 (REST API)

**实现方式**：Spring Cloud OpenFeign

**示例**：Agent Service 调用 DataSource Service

```java
// Agent Service 中定义 Feign Client
@FeignClient(name = "datasource-service", path = "/api/datasource")
public interface DataSourceClient {
    
    @GetMapping("/{id}")
    Datasource getDatasourceById(@PathVariable("id") Long id);
    
    @PostMapping("/test-connection")
    Boolean testConnection(@RequestBody Datasource datasource);
}

// 使用
@Autowired
private DataSourceClient dataSourceClient;

public void validateAgentDatasource(Long datasourceId) {
    Datasource ds = dataSourceClient.getDatasourceById(datasourceId);
    if (!dataSourceClient.testConnection(ds)) {
        throw new BusinessException("数据源连接失败");
    }
}
```

#### 2.3.2 异步通信 (消息队列) - 可选

**实现方式**：Spring Cloud Stream + RabbitMQ/Kafka

**使用场景**：
- 长时间运行的任务（报告生成、批量数据处理）
- 事件驱动场景（数据源状态变更通知）

**优先级**：P2（第二阶段实现）

---

## 三、Consul 集成方案

### 3.1 Consul 架构设计

#### 3.1.1 Consul 部署模式

**开发环境**：单节点模式
```bash
docker run -d \
  --name=consul-dev \
  -p 8500:8500 \
  -p 8600:8600/udp \
  -e CONSUL_BIND_INTERFACE=eth0 \
  consul:1.17 agent -dev -ui -client=0.0.0.0
```

**生产环境**：集群模式（3 节点）
```yaml
# docker-compose-consul.yml
version: '3.8'

services:
  consul-server-1:
    image: consul:1.17
    container_name: consul-server-1
    command: agent -server -ui -bootstrap-expect=3 -node=consul-server-1 -client=0.0.0.0
    ports:
      - "8500:8500"
      - "8600:8600/udp"
    networks:
      - consul-network

  consul-server-2:
    image: consul:1.17
    container_name: consul-server-2
    command: agent -server -node=consul-server-2 -join=consul-server-1
    networks:
      - consul-network

  consul-server-3:
    image: consul:1.17
    container_name: consul-server-3
    command: agent -server -node=consul-server-3 -join=consul-server-1
    networks:
      - consul-network

networks:
  consul-network:
    driver: bridge
```

#### 3.1.2 Consul 功能配置

| 功能         | 配置项                                                    | 说明               |
| ------------ | --------------------------------------------------------- | ------------------ |
| **服务注册** | `spring.cloud.consul.discovery.enabled=true`              | 自动注册到 Consul  |
| **健康检查** | `spring.cloud.consul.discovery.health-check-interval=10s` | 每 10 秒检查一次   |
| **心跳机制** | `spring.cloud.consul.discovery.heartbeat.enabled=true`    | 启用心跳保活       |
| **配置中心** | `spring.cloud.consul.config.enabled=true`                 | 从 Consul 读取配置 |
| **配置格式** | `spring.cloud.consul.config.format=yaml`                  | 使用 YAML 格式     |
| **配置刷新** | `spring.cloud.consul.config.watch.enabled=true`           | 监听配置变化       |

### 3.2 Spring Cloud Consul 依赖配置

#### 3.2.1 根 pom.xml 依赖管理

**文件**：`pom.xml`

**修改内容**：

```xml
<properties>
    <!-- 现有版本 -->
    <spring-ai.version>1.1.0</spring-ai.version>
    <spring-boot.version>3.4.8</spring-boot.version>
    
    <!-- 新增：Spring Cloud 版本 -->
    <spring-cloud.version>2023.0.3</spring-cloud.version>
</properties>

<dependencyManagement>
    <dependencies>
        <!-- 现有依赖 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-dependencies</artifactId>
            <version>${spring-boot.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        
        <!-- 新增：Spring Cloud BOM -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>${spring-cloud.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

**说明**：
- Spring Cloud 2023.0.3 对应 Spring Boot 3.2.x - 3.4.x
- 使用 BOM 统一管理 Spring Cloud 版本

#### 3.2.2 微服务模块通用依赖

每个微服务模块（Agent、DataSource、Chat、Model）的 `pom.xml` 添加：

```xml
<dependencies>
    <!-- Spring Boot Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    
    <!-- Spring Cloud Consul Discovery -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-consul-discovery</artifactId>
    </dependency>
    
    <!-- Spring Cloud Consul Config -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-consul-config</artifactId>
    </dependency>
    
    <!-- Spring Cloud OpenFeign（服务间调用） -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-openfeign</artifactId>
    </dependency>
    
    <!-- Spring Cloud LoadBalancer（负载均衡） -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-loadbalancer</artifactId>
    </dependency>
    
    <!-- Spring Boot Actuator（健康检查） -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
</dependencies>
```

#### 3.2.3 API 网关专用依赖

**文件**：`data-agent-gateway/pom.xml`

```xml
<dependencies>
    <!-- Spring Cloud Gateway -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-gateway</artifactId>
    </dependency>
    
    <!-- Consul Discovery -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-consul-discovery</artifactId>
    </dependency>
    
    <!-- LoadBalancer -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-loadbalancer</artifactId>
    </dependency>
    
    <!-- Actuator -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
</dependencies>
```

### 3.3 微服务配置文件

#### 3.3.1 Agent Service 配置

**文件**：`data-agent-service/src/main/resources/application.yml`

```yaml
server:
  port: 8081

spring:
  application:
    name: agent-service
  
  # Consul 配置
  cloud:
    consul:
      host: ${CONSUL_HOST:localhost}
      port: ${CONSUL_PORT:8500}
      discovery:
        enabled: true
        # 服务实例 ID（确保唯一）
        instance-id: ${spring.application.name}:${server.port}
        # 服务名称（用于服务发现）
        service-name: ${spring.application.name}
        # 健康检查配置
        health-check-path: /actuator/health
        health-check-interval: 10s
        health-check-timeout: 5s
        health-check-critical-timeout: 30s
        # 心跳配置
        heartbeat:
          enabled: true
          ttl: 30s
        # 注册服务的 IP 和端口
        prefer-ip-address: true
        ip-address: ${spring.cloud.client.ip-address:127.0.0.1}
      config:
        enabled: true
        # 配置格式
        format: yaml
        # 配置数据的 Key 前缀
        prefix: config
        # 默认上下文（所有服务共享）
        default-context: application
        # 配置监听
        watch:
          enabled: true
          delay: 1000

  # 数据库配置（可从 Consul 读取）
  datasource:
    url: ${DATA_AGENT_DATASOURCE_URL:jdbc:mysql://127.0.0.1:3306/data_agent}
    username: ${DATA_AGENT_DATASOURCE_USERNAME:cyl}
    password: ${DATA_AGENT_DATASOURCE_PASSWORD:Audaque@123}
    driver-class-name: com.mysql.cj.jdbc.Driver

# Actuator 健康检查配置
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: always

# 日志配置
logging:
  level:
    com.audaque.cloud.ai.dataagent: debug
    org.springframework.cloud.consul: debug
```

#### 3.3.2 API 网关配置

**文件**：`data-agent-gateway/src/main/resources/application.yml`

```yaml
server:
  port: 8080

spring:
  application:
    name: api-gateway
  
  # Consul 配置
  cloud:
    consul:
      host: ${CONSUL_HOST:localhost}
      port: ${CONSUL_PORT:8500}
      discovery:
        enabled: true
        instance-id: ${spring.application.name}:${server.port}
        service-name: ${spring.application.name}
        health-check-path: /actuator/health
        health-check-interval: 10s
        prefer-ip-address: true
    
    # Gateway 路由配置
    gateway:
      discovery:
        locator:
          enabled: true
          lower-case-service-id: true
      routes:
        # Agent 服务路由
        - id: agent-service
          uri: lb://agent-service
          predicates:
            - Path=/api/agent/**
          filters:
            - StripPrefix=1
        
        # 数据源服务路由
        - id: datasource-service
          uri: lb://datasource-service
          predicates:
            - Path=/api/datasource/**
          filters:
            - StripPrefix=1
        
        # 对话服务路由
        - id: chat-service
          uri: lb://chat-service
          predicates:
            - Path=/api/chat/**
          filters:
            - StripPrefix=1
        
        # 模型配置服务路由
        - id: model-service
          uri: lb://model-service
          predicates:
            - Path=/api/model/**
          filters:
            - StripPrefix=1
      
      # 全局 CORS 配置
      globalcors:
        cors-configurations:
          '[/**]':
            allowed-origins: "*"
            allowed-methods:
              - GET
              - POST
              - PUT
              - DELETE
              - OPTIONS
            allowed-headers: "*"
            allow-credentials: false

# Actuator 配置
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,gateway

# 日志配置
logging:
  level:
    org.springframework.cloud.gateway: debug
```

### 3.4 Consul 配置中心使用

#### 3.4.1 配置层级结构

Consul Key/Value 存储结构：

```
config/
├── application/              # 所有服务共享配置
│   └── data/
│       └── (YAML 格式)
│           spring:
│             datasource:
│               url: jdbc:mysql://mysql:3306/data_agent
│               username: cyl
│               password: Audaque@123
│
├── agent-service/            # Agent 服务专属配置
│   └── data/
│       └── (YAML 格式)
│           logging:
│             level:
│               com.audaque: debug
│
├── datasource-service/       # 数据源服务专属配置
│   └── data/
│
├── chat-service/             # 对话服务专属配置
│   └── data/
│
└── model-service/            # 模型服务专属配置
    └── data/
```

#### 3.4.2 配置管理示例

**在 Consul Web UI (http://localhost:8500) 中创建配置**：

**Key**: `config/application/data`

**Value** (YAML):
```yaml
spring:
  datasource:
    url: jdbc:mysql://mysql:3306/data_agent
    username: cyl
    password: Audaque@123
    driver-class-name: com.mysql.cj.jdbc.Driver
    type: com.alibaba.druid.pool.DruidDataSource

mybatis:
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl

logging:
  level:
    root: info
    com.audaque.cloud.ai.dataagent: debug
```

**Key**: `config/agent-service/data`

**Value** (YAML):
```yaml
server:
  port: 8081

# Agent 服务特殊配置
agent:
  max-datasource-count: 10
  enable-batch-import: true
```

#### 3.4.3 动态配置刷新

**使用 `@RefreshScope` 实现配置热更新**：

```java
@Service
@RefreshScope  // 启用配置动态刷新
public class AgentServiceImpl {
    
    @Value("${agent.max-datasource-count:10}")
    private Integer maxDatasourceCount;
    
    public void validateDatasourceCount(int count) {
        // maxDatasourceCount 会在 Consul 配置更新后自动刷新
        if (count > maxDatasourceCount) {
            throw new BusinessException("超过最大数据源数量限制");
        }
    }
}
```

---

## 四、服务拆分实施方案

### 4.1 项目结构调整

#### 4.1.1 新的项目结构

```
DataAgent/
├── pom.xml                           # 父 POM（聚合项目）
├── data-agent-common/                # 公共模块（实体类、工具类）
│   ├── src/main/java/
│   │   └── com/audaque/cloud/ai/dataagent/
│   │       ├── entity/               # 实体类（共享）
│   │       ├── dto/                  # DTO 对象（共享）
│   │       ├── enums/                # 枚举（共享）
│   │       ├── util/                 # 工具类
│   │       └── exception/            # 异常类
│   └── pom.xml
├── data-agent-gateway/               # API 网关
│   ├── src/main/java/
│   │   └── com/audaque/cloud/ai/dataagent/gateway/
│   │       ├── GatewayApplication.java
│   │       ├── config/               # 网关配置
│   │       └── filter/               # 过滤器
│   ├── src/main/resources/
│   │   └── application.yml
│   └── pom.xml
├── data-agent-service/               # Agent 管理服务
│   ├── src/main/java/
│   │   └── com/audaque/cloud/ai/dataagent/agent/
│   │       ├── AgentServiceApplication.java
│   │       ├── controller/
│   │       ├── service/
│   │       ├── mapper/
│   │       └── client/               # Feign 客户端
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── mapper/
│   └── pom.xml
├── data-agent-datasource-service/    # 数据源管理服务
│   ├── src/main/java/
│   │   └── com/audaque/cloud/ai/dataagent/datasource/
│   │       ├── DataSourceServiceApplication.java
│   │       ├── controller/
│   │       ├── service/
│   │       ├── connector/            # 数据库连接器
│   │       └── mapper/
│   └── pom.xml
├── data-agent-chat-service/          # 对话服务
│   ├── src/main/java/
│   │   └── com/audaque/cloud/ai/dataagent/chat/
│   │       ├── ChatServiceApplication.java
│   │       ├── controller/
│   │       ├── service/
│   │       ├── workflow/             # 工作流引擎
│   │       └── mapper/
│   └── pom.xml
├── data-agent-model-service/         # 模型配置服务
│   ├── src/main/java/
│   │   └── com/audaque/cloud/ai/dataagent/model/
│   │       ├── ModelServiceApplication.java
│   │       ├── controller/
│   │       ├── service/
│   │       └── mapper/
│   └── pom.xml
├── data-agent-frontend/              # 前端应用（不变）
│   └── ...
└── docker-file/
    ├── docker-compose-microservices.yml  # 微服务版 Docker Compose
    └── start-microservices.sh            # 微服务启动脚本
```

#### 4.1.2 父 POM 模块配置

**文件**：`pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.audaque.cloud.ai</groupId>
    <artifactId>spring-ai-audaque-data-agent</artifactId>
    <packaging>pom</packaging>
    <version>${revision}</version>

    <!-- 子模块 -->
    <modules>
        <module>data-agent-common</module>
        <module>data-agent-gateway</module>
        <module>data-agent-service</module>
        <module>data-agent-datasource-service</module>
        <module>data-agent-chat-service</module>
        <module>data-agent-model-service</module>
    </modules>

    <properties>
        <revision>1.0.0-SNAPSHOT</revision>
        <java.version>17</java.version>
        <spring-boot.version>3.4.8</spring-boot.version>
        <spring-cloud.version>2023.0.3</spring-cloud.version>
        <spring-ai.version>1.1.0</spring-ai.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <!-- Spring Boot BOM -->
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring-boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>

            <!-- Spring Cloud BOM -->
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>

            <!-- Spring AI BOM -->
            <dependency>
                <groupId>org.springframework.ai</groupId>
                <artifactId>spring-ai-bom</artifactId>
                <version>${spring-ai.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>

            <!-- 内部模块依赖 -->
            <dependency>
                <groupId>com.audaque.cloud.ai</groupId>
                <artifactId>data-agent-common</artifactId>
                <version>${revision}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

### 4.2 代码迁移策略

#### 4.2.1 公共模块提取

**目标**：将单体应用中的公共代码提取到 `data-agent-common` 模块

**提取内容**：
- `entity/` - 所有实体类（Agent、Datasource、ChatSession 等）
- `dto/` - 所有 DTO 对象（请求/响应对象）
- `vo/` - 所有 VO 对象
- `enums/` - 所有枚举类
- `util/` - 工具类（JSON、日期、文件等）
- `exception/` - 异常类和错误码
- `constant/` - 常量定义

**不提取内容**（保留在各微服务）：
- `controller/` - 控制器（各服务独立）
- `service/` - 业务逻辑（各服务独立）
- `mapper/` - MyBatis Mapper（各服务独立）
- `connector/` - 数据库连接器（DataSource Service）
- `workflow/` - 工作流引擎（Chat Service）

#### 4.2.2 Agent Service 代码迁移

**源代码位置**：`data-agent-management/src/main/java/com/audaque/cloud/ai/dataagent/`

**迁移映射**：

| 原路径                                      | 目标路径                         | 说明              |
| ------------------------------------------- | -------------------------------- | ----------------- |
| `controller/AgentController.java`           | `data-agent-service/controller/` | Agent CRUD 控制器 |
| `controller/AgentDatasourceController.java` | `data-agent-service/controller/` | Agent 数据源关联  |
| `controller/PresetQuestionController.java`  | `data-agent-service/controller/` | 预设问题          |
| `service/AgentService.java`                 | `data-agent-service/service/`    | Agent 业务逻辑    |
| `mapper/AgentMapper.java`                   | `data-agent-service/mapper/`     | MyBatis Mapper    |

**新增 Feign 客户端**：

```java
// data-agent-service/client/DataSourceClient.java
@FeignClient(name = "datasource-service", path = "/api/datasource")
public interface DataSourceClient {
    
    @GetMapping("/{id}")
    Datasource getDatasourceById(@PathVariable("id") Long id);
    
    @PostMapping("/test-connection")
    Boolean testConnection(@RequestBody Datasource datasource);
}
```

#### 4.2.3 DataSource Service 代码迁移

**迁移内容**：

| 原路径                                 | 目标路径                                    |
| -------------------------------------- | ------------------------------------------- |
| `controller/DatasourceController.java` | `data-agent-datasource-service/controller/` |
| `service/DatasourceService.java`       | `data-agent-datasource-service/service/`    |
| `connector/**`                         | `data-agent-datasource-service/connector/`  |
| `service/datasource/**`                | `data-agent-datasource-service/service/`    |

#### 4.2.4 Chat Service 代码迁移

**迁移内容**：

| 原路径                           | 目标路径                              |
| -------------------------------- | ------------------------------------- |
| `controller/ChatController.java` | `data-agent-chat-service/controller/` |
| `service/ChatService.java`       | `data-agent-chat-service/service/`    |
| `workflow/**`                    | `data-agent-chat-service/workflow/`   |

#### 4.2.5 Model Service 代码迁移

**迁移内容**：

| 原路径                                        | 目标路径                               |
| --------------------------------------------- | -------------------------------------- |
| `controller/ModelConfigController.java`       | `data-agent-model-service/controller/` |
| `controller/BusinessKnowledgeController.java` | `data-agent-model-service/controller/` |
| `service/ModelConfigService.java`             | `data-agent-model-service/service/`    |

### 4.3 数据库访问策略

#### 4.3.1 数据库访问模式

**方案1：共享数据库（推荐第一阶段）**

所有微服务访问同一个数据库实例，但按模块划分表的访问权限：

```yaml
# 所有服务使用相同的数据库连接
spring:
  datasource:
    url: jdbc:mysql://mysql:3306/data_agent
    username: cyl
    password: Audaque@123
```

**优点**：
- 实施简单，改造成本低
- 事务管理简单（本地事务）
- 数据一致性容易保证

**缺点**：
- 服务间存在数据库层面的耦合
- 难以实现数据库层面的扩展

**方案2：数据库分库（第二阶段优化）**

每个微服务使用独立的数据库：

```
MySQL 实例
├── data_agent_common       # 共享数据库（字典表、配置表）
├── data_agent_agent        # Agent 服务数据库
├── data_agent_datasource   # 数据源服务数据库
├── data_agent_chat         # 对话服务数据库
└── data_agent_model        # 模型服务数据库
```

**优点**：
- 服务完全解耦
- 支持数据库层面独立扩展
- 支持异构数据库（如 Chat Service 使用 MongoDB）

**缺点**：
- 需要分布式事务（Seata）
- 跨服务查询需要通过 API
- 改造成本高

**建议**：先采用方案1，系统稳定后再优化为方案2。

---

## 五、前端适配方案

### 5.1 API 地址调整

#### 5.1.1 当前前端 API 配置

**文件**：`data-agent-frontend/src/services/*.ts`

**当前配置**（直连后端）：
```typescript
// 当前直接访问单体应用
const BASE_URL = 'http://localhost:8065'
```

#### 5.1.2 微服务架构 API 配置

**修改为通过 API 网关访问**：

```typescript
// data-agent-frontend/src/services/common.ts

// 开发环境配置
const DEV_CONFIG = {
  // API 网关地址
  gatewayUrl: 'http://localhost:8080',
  // 服务路由前缀
  services: {
    agent: '/api/agent',
    datasource: '/api/datasource',
    chat: '/api/chat',
    model: '/api/model'
  }
}

// 生产环境配置
const PROD_CONFIG = {
  gatewayUrl: process.env.VITE_API_GATEWAY_URL || 'http://api.dataagent.com',
  services: {
    agent: '/api/agent',
    datasource: '/api/datasource',
    chat: '/api/chat',
    model: '/api/model'
  }
}

const config = import.meta.env.MODE === 'production' ? PROD_CONFIG : DEV_CONFIG

export const API_BASE_URL = config.gatewayUrl
export const SERVICE_ROUTES = config.services
```

#### 5.1.3 API 服务文件修改示例

**文件**：`data-agent-frontend/src/services/agent.ts`

**修改前**：
```typescript
import axios from 'axios'

const BASE_URL = 'http://localhost:8065/agent'

export const getAgentList = async () => {
  const response = await axios.get(`${BASE_URL}/list`)
  return response.data
}
```

**修改后**：
```typescript
import axios from 'axios'
import { API_BASE_URL, SERVICE_ROUTES } from './common'

const AGENT_API = `${API_BASE_URL}${SERVICE_ROUTES.agent}`

export const getAgentList = async () => {
  const response = await axios.get(`${AGENT_API}/list`)
  return response.data
}
```

### 5.2 前端配置文件

**文件**：`data-agent-frontend/.env.development`

```bash
# 开发环境配置
VITE_API_GATEWAY_URL=http://localhost:8080
```

**文件**：`data-agent-frontend/.env.production`

```bash
# 生产环境配置
VITE_API_GATEWAY_URL=https://api.dataagent.com
```

---

## 六、Docker Compose 编排

### 6.1 微服务版 Docker Compose

**文件**：`docker-file/docker-compose-microservices.yml`

```yaml
version: '3.8'

services:
  # Consul 服务注册中心
  consul:
    image: consul:1.17
    container_name: dataagent-consul
    ports:
      - "8500:8500"      # HTTP API
      - "8600:8600/udp"  # DNS
    command: agent -server -ui -bootstrap-expect=1 -client=0.0.0.0 -node=consul-server
    environment:
      - CONSUL_BIND_INTERFACE=eth0
    networks:
      - dataagent-network
    healthcheck:
      test: ["CMD", "consul", "members"]
      interval: 10s
      timeout: 5s
      retries: 5

  # MySQL 数据库
  mysql:
    image: mysql:8.0
    container_name: dataagent-mysql
    ports:
      - "3306:3306"
    environment:
      MYSQL_ROOT_PASSWORD: Audaque@123
      MYSQL_DATABASE: data_agent
      MYSQL_USER: cyl
      MYSQL_PASSWORD: Audaque@123
    volumes:
      - mysql-data:/var/lib/mysql
      - ./config/mysql/init.sql:/docker-entrypoint-initdb.d/init.sql
    networks:
      - dataagent-network
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 10

  # API 网关
  gateway:
    build:
      context: ..
      dockerfile: docker-file/Dockerfile-gateway
    container_name: dataagent-gateway
    ports:
      - "8080:8080"
    environment:
      - CONSUL_HOST=consul
      - CONSUL_PORT=8500
      - SPRING_PROFILES_ACTIVE=prod
    depends_on:
      consul:
        condition: service_healthy
    networks:
      - dataagent-network
    restart: unless-stopped

  # Agent 管理服务
  agent-service:
    build:
      context: ..
      dockerfile: docker-file/Dockerfile-agent-service
    container_name: dataagent-agent-service
    ports:
      - "8081:8081"
    environment:
      - CONSUL_HOST=consul
      - CONSUL_PORT=8500
      - DATA_AGENT_DATASOURCE_URL=jdbc:mysql://mysql:3306/data_agent?useUnicode=true&characterEncoding=utf-8
      - DATA_AGENT_DATASOURCE_USERNAME=cyl
      - DATA_AGENT_DATASOURCE_PASSWORD=Audaque@123
      - SPRING_PROFILES_ACTIVE=prod
    depends_on:
      consul:
        condition: service_healthy
      mysql:
        condition: service_healthy
    networks:
      - dataagent-network
    restart: unless-stopped

  # 数据源管理服务
  datasource-service:
    build:
      context: ..
      dockerfile: docker-file/Dockerfile-datasource-service
    container_name: dataagent-datasource-service
    ports:
      - "8082:8082"
    environment:
      - CONSUL_HOST=consul
      - CONSUL_PORT=8500
      - DATA_AGENT_DATASOURCE_URL=jdbc:mysql://mysql:3306/data_agent?useUnicode=true&characterEncoding=utf-8
      - DATA_AGENT_DATASOURCE_USERNAME=cyl
      - DATA_AGENT_DATASOURCE_PASSWORD=Audaque@123
      - SPRING_PROFILES_ACTIVE=prod
    depends_on:
      consul:
        condition: service_healthy
      mysql:
        condition: service_healthy
    networks:
      - dataagent-network
    restart: unless-stopped

  # 对话服务
  chat-service:
    build:
      context: ..
      dockerfile: docker-file/Dockerfile-chat-service
    container_name: dataagent-chat-service
    ports:
      - "8083:8083"
    environment:
      - CONSUL_HOST=consul
      - CONSUL_PORT=8500
      - DATA_AGENT_DATASOURCE_URL=jdbc:mysql://mysql:3306/data_agent?useUnicode=true&characterEncoding=utf-8
      - DATA_AGENT_DATASOURCE_USERNAME=cyl
      - DATA_AGENT_DATASOURCE_PASSWORD=Audaque@123
      - SPRING_PROFILES_ACTIVE=prod
      # AI 配置
      - OPENAI_API_KEY=${OPENAI_API_KEY}
    depends_on:
      consul:
        condition: service_healthy
      mysql:
        condition: service_healthy
    networks:
      - dataagent-network
    restart: unless-stopped

  # 模型配置服务
  model-service:
    build:
      context: ..
      dockerfile: docker-file/Dockerfile-model-service
    container_name: dataagent-model-service
    ports:
      - "8084:8084"
    environment:
      - CONSUL_HOST=consul
      - CONSUL_PORT=8500
      - DATA_AGENT_DATASOURCE_URL=jdbc:mysql://mysql:3306/data_agent?useUnicode=true&characterEncoding=utf-8
      - DATA_AGENT_DATASOURCE_USERNAME=cyl
      - DATA_AGENT_DATASOURCE_PASSWORD=Audaque@123
      - SPRING_PROFILES_ACTIVE=prod
    depends_on:
      consul:
        condition: service_healthy
      mysql:
        condition: service_healthy
    networks:
      - dataagent-network
    restart: unless-stopped

  # 前端应用
  frontend:
    build:
      context: ..
      dockerfile: docker-file/Dockerfile-frontend
    container_name: dataagent-frontend
    ports:
      - "3000:3000"
    environment:
      - VITE_API_GATEWAY_URL=http://localhost:8080
    depends_on:
      - gateway
    networks:
      - dataagent-network
    restart: unless-stopped

networks:
  dataagent-network:
    driver: bridge

volumes:
  mysql-data:
```

### 6.2 微服务启动脚本

**文件**：`docker-file/start-microservices.sh`

```bash
#!/bin/bash

# Copyright 2024-2026 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -e

echo "=================================================="
echo "  启动 DataAgent 微服务架构"
echo "=================================================="

# 检查 Docker 和 Docker Compose
if ! command -v docker &> /dev/null; then
    echo "错误: Docker 未安装"
    exit 1
fi

if ! docker compose version &> /dev/null; then
    echo "错误: Docker Compose 未安装"
    exit 1
fi

# 停止现有容器
if docker compose -f docker-compose-microservices.yml ps | grep -q "Up"; then
    echo "检测到正在运行的容器，正在停止..."
    docker compose -f docker-compose-microservices.yml down
fi

# 清理未使用的资源
echo "清理未使用的 Docker 资源..."
docker system prune -f

# 构建并启动服务
echo "构建并启动微服务..."
if ! docker compose -f docker-compose-microservices.yml up -d --build; then
    echo ""
    echo "=================================================="
    echo "  错误：服务启动失败！"
    echo "=================================================="
    echo "查看详细错误信息："
    echo "  docker compose -f docker-compose-microservices.yml logs"
    exit 1
fi

# 等待服务启动
echo "等待服务启动..."
sleep 10

# 检查 Consul 健康状态
echo "检查 Consul 健康状态..."
until curl -f http://localhost:8500/v1/status/leader &>/dev/null; do
    echo "等待 Consul 启动..."
    sleep 5
done
echo "✓ Consul 已启动"

# 显示服务状态
echo ""
echo "=================================================="
echo "  服务状态"
echo "=================================================="
docker compose -f docker-compose-microservices.yml ps

# 检查服务注册
echo ""
echo "=================================================="
echo "  Consul 服务注册状态"
echo "=================================================="
curl -s http://localhost:8500/v1/agent/services | jq '.'

echo ""
echo "=================================================="
echo "  访问地址"
echo "=================================================="
echo "Consul UI:    http://localhost:8500"
echo "API 网关:     http://localhost:8080"
echo "前端应用:     http://localhost:3000"
echo ""
echo "微服务端口:"
echo "  Agent Service:      http://localhost:8081"
echo "  DataSource Service: http://localhost:8082"
echo "  Chat Service:       http://localhost:8083"
echo "  Model Service:      http://localhost:8084"
echo ""
echo "查看日志: docker compose -f docker-compose-microservices.yml logs -f [service-name]"
echo "=================================================="
```

---

## 七、监控与日志

### 7.1 服务监控方案

#### 7.1.1 Spring Boot Actuator

**所有微服务自动暴露监控端点**：

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
  metrics:
    tags:
      application: ${spring.application.name}
```

**监控端点**：
- `http://localhost:8081/actuator/health` - 健康检查
- `http://localhost:8081/actuator/metrics` - 性能指标
- `http://localhost:8081/actuator/info` - 服务信息

#### 7.1.2 Consul 健康检查

Consul 自动监控服务健康状态：

- 每 10 秒调用 `/actuator/health` 检查服务状态
- 服务异常时自动从服务列表摘除
- 服务恢复后自动重新注册

**在 Consul UI 查看**：
- 访问 http://localhost:8500
- 查看 Services 列表
- 绿色表示健康，红色表示异常

#### 7.1.3 Prometheus + Grafana（可选）

**部署 Prometheus 采集指标**：

```yaml
# docker-compose-monitoring.yml
services:
  prometheus:
    image: prom/prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./config/prometheus.yml:/etc/prometheus/prometheus.yml
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'

  grafana:
    image: grafana/grafana
    ports:
      - "3001:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
```

**Prometheus 配置** (`config/prometheus.yml`):

```yaml
global:
  scrape_interval: 15s

scrape_configs:
  # 通过 Consul 自动发现服务
  - job_name: 'consul-services'
    consul_sd_configs:
      - server: 'consul:8500'
    relabel_configs:
      - source_labels: [__meta_consul_service]
        target_label: service
```

### 7.2 日志聚合方案

#### 7.2.1 统一日志格式

**所有微服务使用统一的日志格式**：

```yaml
# application.yml
logging:
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{traceId}] %-5level %logger{36} - %msg%n"
  level:
    root: info
    com.audaque.cloud.ai.dataagent: debug
```

#### 7.2.2 分布式追踪（可选）

**集成 Spring Cloud Sleuth + Zipkin**：

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-sleuth</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-sleuth-zipkin</artifactId>
</dependency>
```

**Zipkin 配置**：

```yaml
spring:
  zipkin:
    base-url: http://zipkin:9411
  sleuth:
    sampler:
      probability: 1.0  # 采样率 100%
```

---

## 八、测试验证方案

### 8.1 单元测试

**每个微服务独立测试**：

```java
@SpringBootTest
@AutoConfigureMockMvc
class AgentServiceTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void testGetAgentList() throws Exception {
        mockMvc.perform(get("/api/agent/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
```

### 8.2 集成测试

**测试服务间调用**：

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@EnableFeignClients
class AgentServiceIntegrationTest {
    
    @Autowired
    private DataSourceClient dataSourceClient;
    
    @Test
    void testDataSourceClient() {
        Datasource ds = dataSourceClient.getDatasourceById(1L);
        assertNotNull(ds);
    }
}
```

### 8.3 端到端测试

**测试完整业务流程**：

1. **前端发起请求** → `http://localhost:3000`
2. **API 网关路由** → `http://localhost:8080/api/agent/list`
3. **Agent Service 处理** → `http://localhost:8081/list`
4. **调用 DataSource Service** → `http://localhost:8082/test-connection`
5. **返回结果** → 前端展示

**验证步骤**：
```bash
# 1. 启动所有服务
./start-microservices.sh

# 2. 检查 Consul 服务注册
curl http://localhost:8500/v1/agent/services

# 3. 通过 API 网关测试
curl http://localhost:8080/api/agent/list

# 4. 直接访问微服务测试
curl http://localhost:8081/list
```

---

## 九、实施计划

### 9.1 第一阶段：基础架构搭建（2 周）

**目标**：完成微服务基础框架搭建

**任务清单**：
- [ ] 引入 Spring Cloud 依赖
- [ ] 部署 Consul 集群
- [ ] 创建 API 网关模块
- [ ] 创建公共模块 (data-agent-common)
- [ ] 配置 Consul 服务注册和配置中心
- [ ] 编写 Docker Compose 编排文件
- [ ] 测试服务注册和发现功能

### 9.2 第二阶段：服务拆分（3 周）

**目标**：完成核心服务拆分

**任务清单**：
- [ ] 拆分 Agent Service
- [ ] 拆分 DataSource Service
- [ ] 拆分 Chat Service
- [ ] 拆分 Model Service
- [ ] 实现 Feign 客户端
- [ ] 测试服务间通信
- [ ] 前端 API 地址适配

### 9.3 第三阶段：功能验证（2 周）

**目标**：确保所有功能可用

**任务清单**：
- [ ] 单元测试覆盖
- [ ] 集成测试覆盖
- [ ] 端到端测试
- [ ] 性能测试
- [ ] 压力测试
- [ ] 故障恢复测试

### 9.4 第四阶段：监控完善（1 周）

**目标**：完善监控和日志

**任务清单**：
- [ ] 配置 Actuator 监控
- [ ] 部署 Prometheus + Grafana
- [ ] 配置日志聚合
- [ ] 配置分布式追踪
- [ ] 编写运维文档

---

## 十、风险与应对

### 10.1 技术风险

| 风险                 | 影响             | 应对措施                               |
| -------------------- | ---------------- | -------------------------------------- |
| **服务拆分粒度不当** | 服务过多或过少   | 先粗粒度拆分，后续根据实际情况优化     |
| **分布式事务问题**   | 数据一致性难保证 | 第一阶段使用共享数据库，避免分布式事务 |
| **服务间调用性能**   | 响应时间增加     | 使用 HTTP/2，启用连接池，合理设计 API  |
| **配置管理复杂度**   | 配置文件分散     | 使用 Consul 配置中心统一管理           |

### 10.2 业务风险

| 风险               | 影响         | 应对措施                         |
| ------------------ | ------------ | -------------------------------- |
| **现有功能受影响** | 用户体验下降 | 充分测试，灰度发布，保留回滚方案 |
| **开发周期延长**   | 项目延期     | 采用渐进式改造，核心功能优先     |
| **学习成本高**     | 团队效率降低 | 提供培训，编写详细文档           |

### 10.3 运维风险

| 风险               | 影响         | 应对措施                       |
| ------------------ | ------------ | ------------------------------ |
| **部署复杂度增加** | 运维难度提升 | 使用 Docker Compose 简化部署   |
| **故障排查困难**   | 问题定位慢   | 完善日志和监控，使用分布式追踪 |
| **服务依赖管理**   | 版本冲突     | 使用 Maven BOM 统一版本管理    |

---

## 十一、附录

### 11.1 关键技术栈版本

| 组件         | 版本     | 说明               |
| ------------ | -------- | ------------------ |
| Spring Boot  | 3.4.8    | 应用框架           |
| Spring Cloud | 2023.0.3 | 微服务框架         |
| Spring AI    | 1.1.0    | AI 集成            |
| Consul       | 1.17     | 服务注册与配置中心 |
| MySQL        | 8.0      | 数据库             |
| Java         | 17       | JDK 版本           |
| Maven        | 3.9.9    | 构建工具           |

### 11.2 参考资料

- [Spring Cloud 官方文档](https://spring.io/projects/spring-cloud)
- [Consul 官方文档](https://www.consul.io/docs)
- [Spring Cloud Gateway 文档](https://spring.io/projects/spring-cloud-gateway)
- [微服务架构设计模式](https://microservices.io/patterns/index.html)

### 11.3 文档变更记录

| 版本  | 日期       | 作者           | 变更说明                     |
| ----- | ---------- | -------------- | ---------------------------- |
| 1.0.0 | 2026-01-21 | DataAgent Team | 初始版本，定义微服务改造方案 |

---

**注意事项**：
1. 本文档为需求规范，实际实施时应结合项目情况调整
2. 建议采用渐进式改造，降低风险
3. 保持向后兼容，确保现有功能不受影响
4. 充分测试后再上线生产环境

**审批流程**：
- [ ] 技术负责人审批
- [ ] 架构师审批
- [ ] 项目经理审批
