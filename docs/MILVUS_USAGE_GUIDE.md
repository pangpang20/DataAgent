# Milvus 基本使用说明

本文档介绍 Milvus 向量数据库的常用操作，包括集合管理、数据查询等。

## 连接信息

默认连接参数：
- **地址**: `http://localhost:19530`
- **用户名**: `root`
- **密码**: `Milvus`

## 集合管理

### 查看集合 Schema

**方式一：使用 curl**

```bash
curl -X POST 'http://localhost:19530/v2/vectordb/collections/describe' \
  -H 'Authorization: Bearer root:Milvus' \
  -H 'Content-Type: application/json' \
  -d '{
    "collectionName": "data_agent_vector"
  }'
```

**方式二：使用 Python (pymilvus)**

```bash
python3.10 <<'EOF'
from pymilvus import connections, Collection
connections.connect(uri="http://localhost:19530", token="root:Milvus")

col = Collection("data_agent_vector")
print(col.schema)
EOF
```

### 删除集合

**方式一：使用 curl**

```bash
# 设置连接信息
export CLUSTER_ENDPOINT="http://localhost:19530"
export TOKEN="root:Milvus"

# 删除集合
curl --request POST \
  --url "${CLUSTER_ENDPOINT}/v2/vectordb/collections/drop" \
  --header "Authorization: Bearer ${TOKEN}" \
  --header "Content-Type: application/json" \
  -d '{
    "collectionName": "data_agent_vector"
  }'
```

**方式二：使用 Python (pymilvus)**

```bash
python3.10 -c "
from pymilvus import connections, utility
connections.connect(host='localhost', port='19530', user='root', password='Milvus')
utility.drop_collection('data_agent_vector')
print('集合已删除')
"
```

## 数据查询

### 查看集合中的所有数据

```bash
python3.10 <<'EOF'
from pymilvus import connections, Collection
connections.connect(uri="http://localhost:19530", token="root:Milvus")

col = Collection("data_agent_vector")
col.load()

res = col.query(
    expr='doc_id != ""',
    output_fields=["doc_id", "content", "metadata"],
    limit=1000
)

for r in res:
    print(r)
EOF
```

### 按条件查询数据

```bash
python3.10 <<'EOF'
from pymilvus import connections, Collection
connections.connect(uri="http://localhost:19530", token="root:Milvus")

col = Collection("data_agent_vector")
col.load()

# 按文档ID查询
res = col.query(
    expr='doc_id == "your_doc_id"',
    output_fields=["doc_id", "content", "metadata"],
    limit=10
)

for r in res:
    print(r)
EOF
```

### 统计集合中的数据量

```bash
python3.10 <<'EOF'
from pymilvus import connections, Collection
connections.connect(uri="http://localhost:19530", token="root:Milvus")

col = Collection("data_agent_vector")
print(f"集合中的数据量: {col.num_entities}")
EOF
```

## 常用操作

### 列出所有集合

```bash
python3.10 <<'EOF'
from pymilvus import connections, utility
connections.connect(uri="http://localhost:19530", token="root:Milvus")

collections = utility.list_collections()
print("所有集合:", collections)
EOF
```

### 检查集合是否存在

```bash
python3.10 <<'EOF'
from pymilvus import connections, utility
connections.connect(uri="http://localhost:19530", token="root:Milvus")

exists = utility.has_collection("data_agent_vector")
print(f"集合是否存在: {exists}")
EOF
```

### 释放集合内存

```bash
python3.10 <<'EOF'
from pymilvus import connections, Collection
connections.connect(uri="http://localhost:19530", token="root:Milvus")

col = Collection("data_agent_vector")
col.release()
print("集合内存已释放")
EOF
```

## 注意事项

1. **删除集合是不可逆操作**：删除集合后，其中的所有数据将永久丢失，请谨慎操作。
2. **集合名称**：DataAgent 默认使用的集合名称为 `data_agent_vector`。
3. **维度匹配**：创建新集合时，向量维度必须与 Embedding 模型输出维度一致。详见 [Embedding维度修改指南](EMBEDDING_DIMENSION_GUIDE.md)。
4. **连接超时**：如果连接超时，请检查 Milvus 服务是否正常运行：
   ```bash
   docker ps | grep milvus
   ```

## 相关文档

- [Milvus启动配置指南](MILVUS_STARTUP_GUIDE.md) - Milvus 服务启动与自启动配置
- [Embedding维度修改指南](EMBEDDING_DIMENSION_GUIDE.md) - Embedding 模型维度配置
