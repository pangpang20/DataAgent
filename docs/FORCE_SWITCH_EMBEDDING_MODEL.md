# 强制切换 Embedding 模型功能说明

## 问题背景

在之前的版本中，当用户需要切换不同维度的 Embedding 模型时，会遇到以下死循环问题：

1. **已启用1024维模型** → 想切换到2560维模型
2. **启用2560维模型** → ❌ 提示"维度不匹配，无法启用"
3. **删除1024维模型** → ❌ 提示"已启用的模型不能删除，请先启用其他模型"
4. **结果**：无法切换，陷入死循环

## 解决方案

新增**"强制切换 Embedding 模型"**功能，允许用户在明确风险的情况下切换不同维度的模型。

## 功能说明

### 1. 后端 API

新增接口：`POST /api/model-config/force-activate/{id}`

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

### 2. 前端交互流程

当用户尝试启用 Embedding 模型时：

**步骤 1**：显示第一次确认对话框
```
您正在更换嵌入模型，此操作风险较高！
由于不同模型的向量空间不一致，切换后可能导致所有历史向量数据
（含数据源、智能体知识、业务知识）将全部失效且无法检索。
确定要执行吗？
```

**步骤 2**：如果维度不匹配，显示第二次确认对话框
```
维度不匹配！Collection 维度: 1024, 新模型维度: 2560。
请选择以下解决方案之一：
1. 删除现有 collection 并重新创建（会丢失所有向量数据）
2. 修改配置使用新的 collection 名称（保留旧数据）
3. 使用维度匹配的 Embedding 模型

是否强制切换？这将删除现有向量库并重建，所有历史向量数据将永久丢失！
```

**步骤 3**：用户确认后，执行强制切换
- 删除现有 Milvus collection
- 更新内存中的维度配置
- 启用新的 Embedding 模型
- **提示用户重启应用**

**步骤 4**：重启应用
- 系统自动创建新的 collection（新维度）
- 可以开始重新向量化数据

## 使用场景

### 场景 1：从低维度模型切换到高维度模型

**示例**：从 `bge-large-zh` (1024维) 切换到 `qwen3-embedding:4b` (2560维)

1. 在模型配置页面，点击新模型的"启用"按钮
2. 确认第一次警告对话框
3. 确认第二次强制切换对话框
4. 系统提示"模型强制切换成功！向量库已删除，维度配置已更新。请重启应用以创建新的向量库。"
5. **重启应用**
6. 系统自动创建新的 collection（2560维）
7. 重新进行数据向量化

### 场景 2：从高维度模型切换到低维度模型

**示例**：从 `text-embedding-3-large` (3072维) 切换到 `text-embedding-3-small` (1536维)

操作流程同上。

## 技术实现

### 后端实现

**文件**：`VectorDimensionService.java`
- 新增方法：`dropCurrentCollection()` - 删除当前 Milvus collection
- 新增方法：`updateConfiguredDimension(int newDimension)` - 更新内存中的维度配置

**文件**：`ModelConfigOpsService.java`
- 新增方法：`forceActivateConfig(Integer id)` - 强制激活配置
- 逻辑：
  1. 检查维度兼容性
  2. 如果不兼容，删除现有 collection
  3. 更新内存中的维度配置
  4. 激活新模型
  5. 更新数据库状态

**文件**：`ModelConfigController.java`
- 新增接口：`POST /force-activate/{id}`
- 返回消息提示用户重启应用

### 前端实现

**文件**：`modelConfig.ts`
- 新增方法：`forceActivate(id: number)` - 调用强制激活 API

**文件**：`ModelConfig.vue`
- 修改 `handleActivate` 方法
- 增加维度不匹配的检测和强制切换逻辑

## 风险提示

⚠️ **重要警告**：

1. **数据丢失**：强制切换会删除所有历史向量数据
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

## 相关文档

- [Embedding 模型维度修改指南](./EMBEDDING_DIMENSION_GUIDE.md)
- [Milvus 使用指南](./MILVUS_USAGE_GUIDE.md)
- [Milvus 启动指南](./MILVUS_STARTUP_GUIDE.md)

## 常见问题

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

### Q: 如何验证新 collection 已创建？

A: 重启应用后，检查日志中是否有类似以下信息：
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

## 更新日志

**版本**：v1.2.0
**日期**：2026-03-07
**变更**：
- 新增强制切换 Embedding 模型功能
- 解决维度不匹配导致的死循环问题
- 优化用户交互流程，提供更清晰的风险提示
- 添加维度配置自动更新功能
- 明确提示用户需要重启应用
