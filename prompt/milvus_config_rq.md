# 向量数据库可配置化 (Milvus 支持) 需求文档

## 1. 背景与目标
当前系统默认使用 Spring AI 的 `SimpleVectorStore`（内存实现），数据在应用重启后会丢失。为了满足生产环境对向量数据持久化和高性能检索的需求，需要支持切换到 **Milvus** 向量数据库。

**目标**：
- 支持通过配置文件切换向量数据库类型。
- 默认保持为 `simple`（内存模式）。
- 生产环境可选 `milvus` 模式。
- 更新一键部署脚本，支持 Milvus 相关参数配置。

## 2. 核心修改点梳理

### 2.1 依赖管理 (pom.xml)
- **文件**：`data-agent-management/pom.xml`
- **变更**：添加 `spring-ai-milvus-store-spring-boot-starter` 依赖。
- **说明**：确保 Spring AI 版本与当前项目保持一致 (${spring-ai.version})。

### 2.2 配置类扩展 (Properties)
- **文件**：`data-agent-management/src/main/java/com/audaque/cloud/ai/dataagent/properties/DataAgentProperties.java`
- **变更**：
    - 在 `VectorStoreProperties` 内部类中增加 `type` 字段（可选值：`simple`, `milvus`）。
    - 增加 Milvus 特有配置项（或依赖 Spring AI 标准配置前缀 `spring.ai.vectorstore.milvus.*`）。
- **建议**：优先利用 Spring AI 原生的配置项，但在 `DataAgentProperties` 中保留类型切换的显式声明。

### 2.3 自动配置重构 (Configuration)
- **文件**：`data-agent-management/src/main/java/com/audaque/cloud/ai/dataagent/config/DataAgentConfiguration.java`
- **变更**：
    - 修改 `simpleVectorStore` Bean 的条件注解。
    - 确保当 `spring.ai.vectorstore.type=milvus` 时，`simpleVectorStore` 不被初始化，转而由 Milvus Starter 自动配置的 Bean 接管。
- **关键点**：保持 `@Primary` 的一致性，确保业务代码中注入的 `VectorStore` 能够正确识别。

### 2.4 默认配置文件 (application.yml)
- **文件**：`data-agent-management/src/main/resources/application.yml`
- **变更**：
    - 增加 `spring.ai.vectorstore.type: ${VECTOR_STORE_TYPE:simple}`。
    - 增加 Milvus 配置模板注释，方便用户参考。

### 2.5 一键部署脚本 (deploy-dataagent.sh)
- **文件**：`deploy-dataagent.sh.sample`
- **变更**：
    - 增加变量：`VECTOR_STORE_TYPE` (默认 simple)。
    - 增加 Milvus 变量：`MILVUS_HOST`, `MILVUS_PORT`, `MILVUS_COLLECTION` 等。
    - 修改 `deploy_backend` 函数中的 `sed` 逻辑，支持将 Milvus 配置写入生成的 `application.yml`。

## 3. 详细修改清单

| 修改项        | 文件路径                        | 修改内容描述                                         |
| :------------ | :------------------------------ | :--------------------------------------------------- |
| **依赖**      | `data-agent-management/pom.xml` | 添加 `spring-ai-milvus-store-spring-boot-starter`    |
| **属性定义**  | `DataAgentProperties.java`      | `VectorStoreProperties` 增加 `type` 字段             |
| **Bean 配置** | `DataAgentConfiguration.java`   | 更新 `simpleVectorStore` 的 `@ConditionalOnProperty` |
| **应用配置**  | `application.yml`               | 增加向量库类型配置项及 Milvus 示例                   |
| **部署脚本**  | `deploy-dataagent.sh.sample`    | 增加 Milvus 配置项并实现配置自动注入                 |

## 4. 生产环境建议
在生产环境中，建议将 `VECTOR_STORE_TYPE` 设置为 `milvus`，并配置独立的 Milvus 服务实例。系统启动后，原有的内存向量数据由于无法迁移，需要重新触发 Schema 同步或知识库上传操作。
