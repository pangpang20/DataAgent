# Embedding 模型维度修改指南

## 背景

Milvus 向量数据库的 collection 在创建时会指定向量维度（dimension），该维度必须与 Embedding 模型生成的向量维度一致。如果切换到不同维度的 Embedding 模型，需要重新创建 collection。

**常见 Embedding 模型维度参考：**

| 模型 | 维度 |
|------|------|
| text-embedding-ada-002 | 1536 |
| text-embedding-3-small | 1536 |
| text-embedding-3-large | 3072 |
| bge-large-zh | 1024 |
| bge-base-zh | 768 |
| qwen3-embedding:4b | 2560 |

## 切换方式

系统提供两种切换 Embedding 模型维度的方式：

### 方式一：界面强制切换（推荐）

通过 Web 界面进行强制切换，系统会自动处理大部分操作。

#### 问题背景

在之前的版本中，当用户需要切换不同维度的 Embedding 模型时，会遇到以下死循环问题：

1. **已启用1024维模型** → 想切换到2560维模型
2. **启用2560维模型** → ❌ 提示"维度不匹配，无法启用"
3. **删除1024维模型** → ❌ 提示"已启用的模型不能删除，请先启用其他模型"
4. **结果**：无法切换，陷入死循环

#### 解决方案

新增**"强制切换 Embedding 模型"**功能，允许用户在明确风险的情况下切换不同维度的模型。

#### 操作步骤

**步骤 1**：在模型配置页面，点击新模型的"启用"按钮

**步骤 2**：确认第一次警告对话框
```
您正在更换嵌入模型，此操作风险较高！
由于不同模型的向量空间不一致，切换后可能导致所有历史向量数据
（含数据源、智能体知识、业务知识）将全部失效且无法检索。
确定要执行吗？
```

**步骤 3**：如果维度不匹配，确认第二次强制切换对话框
```
维度不匹配！Collection 维度: 1024, 新模型维度: 2560。
请选择以下解决方案之一：
1. 删除现有 collection 并重新创建（会丢失所有向量数据）
2. 修改配置使用新的 collection 名称（保留旧数据）
3. 使用维度匹配的 Embedding 模型

是否强制切换？这将删除现有向量库并重建，所有历史向量数据将永久丢失！
```

**步骤 4**：系统执行强制切换
- 删除现有 Milvus collection
- 更新内存中的维度配置
- 启用新的 Embedding 模型
- 提示用户重启应用

**步骤 5**：**重启应用**（必须）
```bash
# 如果使用 Docker
docker restart dataagent

# 如果使用 JAR 包
# 停止当前应用，然后重新启动
java -jar dataagent.jar
```

**步骤 6**：验证新 collection 已创建

重启应用后，检查日志中是否有类似以下信息：
```
MilvusVectorStore - Creating collection 'data_agent_vector' with dimension 2560
```

或者使用 Milvus API 查询：
```bash
curl -X POST 'http://localhost:19530/v2/vectordb/collections/describe' \
  -H 'Authorization: Bearer root:Milvus' \
  -H 'Content-Type: application/json' \
  -d '{"collectionName": "data_agent_vector"}'
```

**步骤 7**：重新向量化数据
- 重新向量化所有数据源
- 重新向量化智能体知识
- 重新向量化业务知识
- 测试检索功能是否正常

#### 技术实现

**后端 API**：`POST /api/model-config/force-activate/{id}`

**功能**：
- 强制激活指定的 Embedding 模型配置
- 如果维度不匹配，自动删除现有的 Milvus collection
- 更新内存中的维度配置
- **需要重启应用**以创建新的 collection

**注意事项**：
- ⚠️ 此操作会删除所有历史向量数据
- ⚠️ 数据源、智能体知识、业务知识的向量数据将全部丢失
- ⚠️ **必须重启应用**才能创建新的 collection
- ⚠️ 需要重新进行向量化操作

### 方式二：手动修改配置

通过手动修改配置文件和删除 collection 来切换维度。

#### 步骤 1：删除现有 Collection

使用 Milvus API 删除现有的 collection：

```bash
# 设置 Milvus 连接信息
export CLUSTER_ENDPOINT="http://localhost:19530"
export TOKEN="root:Milvus"

# 删除 collection
curl --request POST \
--url "${CLUSTER_ENDPOINT}/v2/vectordb/collections/drop" \
--header "Authorization: Bearer ${TOKEN}" \
--header "Content-Type: application/json" \
-d '{
    "collectionName": "data_agent_vector"
}'
```

**注意**：删除 collection 会丢失所有向量数据，请确保已备份重要数据。

#### 步骤 2：修改配置文件

修改 `application.yml` 中的 `embedding-dimension` 配置：

```yaml
spring:
  ai:
    vectorstore:
      milvus:
        embedding-dimension: 2560  # 修改为新 Embedding 模型的维度
```

或者用命令（推荐）

```bash
sed -i '/embedding-dimension:/s/1024/2560/' application.yml
sed -i '/default-embedding-dimension:/s/1024/2560/' application.yml
```

或者通过环境变量配置：

```bash
export SPRING_AI_VECTORSTORE_MILVUS_EMBEDDING_DIMENSION=2560
```

#### 步骤 3：重启应用

重启 DataAgent 应用，系统会自动创建新的 collection：

```bash
# 如果使用 Docker
docker restart dataagent

# 如果使用 JAR 包
java -jar dataagent.jar
```

## 验证

启动后检查日志，确认 collection 创建成功：

```bash
curl -X POST 'http://localhost:19530/v2/vectordb/collections/describe' \
  -H 'Authorization: Bearer root:Milvus' \
  -H 'Content-Type: application/json' \
  -d '{
    "collectionName": "data_agent_vector"
  }'
```

也可以通过 API 查询当前向量库维度：

http://172.16.1.137:9091/webui/collections

## 风险提示

⚠️ **重要警告**：

1. **数据丢失**：切换不同维度的模型会删除所有历史向量数据
2. **重新向量化**：需要重新对数据源、知识库进行向量化
3. **服务中断**：在重新向量化完成前，检索功能将不可用
4. **不可恢复**：删除的向量数据无法恢复
5. **需要重启**：必须重启应用才能创建新的 collection

## 最佳实践

### 1. 切换前备份

如果需要保留旧数据，建议：
- 使用新的 collection 名称（修改配置文件）
- 或导出重要数据后再切换

### 2. 切换时机

建议在以下时机进行切换：
- 系统维护窗口期
- 用户访问量较低的时段
- 有足够时间重新向量化数据

### 3. 切换后操作

切换完成后需要：
1. **重启应用**（必须）
2. 验证新 collection 已创建（检查日志）
3. 重新向量化所有数据源
4. 重新向量化智能体知识
5. 重新向量化业务知识
6. 测试检索功能是否正常

## 常见问题

### Q: 切换 Embedding 模型时报维度不匹配错误？

**错误信息**：
```
Incorrect dimension for field 'embedding': the no.0 vector's dimension: 2560 is not equal to field's dimension: 1024
```

**解决方案**：
- **推荐**：使用界面强制切换功能，系统会自动处理
- **手动**：按照上述步骤删除旧 collection 并修改维度配置后重启

### Q: 不想丢失数据怎么办？

如果需要保留旧数据，可以使用新的 collection 名称：

```yaml
spring:
  ai:
    vectorstore:
      milvus:
        collection-name: data_agent_vector_v2  # 使用新的 collection 名称
        embedding-dimension: 2560
```

### Q: 如何查看当前 collection 的维度？

```bash
curl --request POST \
--url "${CLUSTER_ENDPOINT}/v2/vectordb/collections/describe" \
--header "Authorization: Bearer ${TOKEN}" \
--header "Content-Type: application/json" \
-d '{
    "collectionName": "data_agent_vector"
}'
```

### Q: 强制切换后，旧数据还能恢复吗？

A: 不能。强制切换会永久删除所有向量数据，请谨慎操作。

### Q: 能否在不删除数据的情况下切换模型？

A: 可以，但需要：
1. 修改配置文件，使用新的 collection 名称
2. 或使用维度相同的 Embedding 模型

### Q: 强制切换需要多长时间？

A: 切换操作本身很快（几秒），但需要重启应用，重新向量化数据可能需要较长时间，取决于数据量大小。

### Q: 切换过程中系统还能使用吗？

A: 切换期间检索功能不可用，其他功能（如对话）不受影响。重启后需要重新向量化数据。

### Q: 为什么需要重启应用？

A: Spring AI 的 MilvusVectorStore 在应用启动时根据配置创建 collection。删除 collection 后，需要重启应用才能触发新 collection 的创建。

## 相关配置项

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `spring.ai.vectorstore.milvus.collection-name` | Milvus collection 名称 | `data_agent_vector` |
| `spring.ai.vectorstore.milvus.embedding-dimension` | 向量维度 | `1024` |
| `spring.ai.vectorstore.milvus.initialize-schema` | 是否自动创建 collection | `true` |

## 相关文档

- [Milvus 使用指南](./MILVUS_USAGE_GUIDE.md)
- [Milvus 启动指南](./MILVUS_STARTUP_GUIDE.md)

## 更新日志

**版本**：v1.2.0
**日期**：2026-03-07
**变更**：
- 新增强制切换 Embedding 模型功能（界面操作）
- 解决维度不匹配导致的死循环问题
- 优化用户交互流程，提供更清晰的风险提示
- 添加维度配置自动更新功能
- 明确提示用户需要重启应用
- 整合手动修改和界面切换两种方式
