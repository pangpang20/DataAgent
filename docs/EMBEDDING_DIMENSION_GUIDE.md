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

## 修改步骤

### 步骤 1：删除现有 Collection

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

### 步骤 2：修改配置文件

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

### 步骤 3：重启应用

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


## 常见问题

### Q: 切换 Embedding 模型时报维度不匹配错误？

**错误信息**：
```
Incorrect dimension for field 'embedding': the no.0 vector's dimension: 2560 is not equal to field's dimension: 1024
```

**解决方案**：按照上述步骤删除旧 collection 并修改维度配置后重启。

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

## 相关配置项

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `spring.ai.vectorstore.milvus.collection-name` | Milvus collection 名称 | `data_agent_vector` |
| `spring.ai.vectorstore.milvus.embedding-dimension` | 向量维度 | `1024` |
| `spring.ai.vectorstore.milvus.initialize-schema` | 是否自动创建 collection | `true` |
