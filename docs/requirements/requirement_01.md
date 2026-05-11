# 需求：报表图表图片持久化与 URL 化

## 背景

当前系统中，Python 执行节点（`PythonExecuteNode`）通过 matplotlib 生成的图表以 base64 编码的 PNG 图片形式存在，存在以下问题：

1. **base64 内联图片体积膨胀** — base64 编码比原始二进制大约 33%，增加 SSE 传输和 LLM prompt 的 token 消耗
2. **图片不可复用** — 每次查询都重新生成，无法缓存或分享
3. **Markdown 报表中图片无法正常渲染** — 当报表以 Markdown 格式输出时，`![chart](data:image/png;base64,...)` 这种 data URI 在很多 Markdown 渲染器中不被支持（如 GitHub、邮件客户端、部分编辑器）
4. **图片未持久化** — 图片仅存在于 SSE 流中，会话结束后无法回溯查看

## 目标

将 matplotlib 生成的图表图片持久化存储，生成可访问的 HTTP URL，在 Markdown 报表中以网络图片方式引用。

## 当前流程

```
Python 脚本 (matplotlib)
  → base64 PNG 字符串 (JSON chart_image 字段)
    → PythonExecuteNode 解析
      → SSE 流: <img src="data:image/png;base64,...">
      → 传递给 ReportGeneratorNode
        → 嵌入 LLM prompt: ![chart](data:image/png;base64,...)
```

**现有文件存储能力：**
- `FileStorageService` 接口已有 `storeFile(MultipartFile)` 和 `getFileUrl(filePath)` 方法
- 支持两种后端：`LocalFileStorageServiceImpl`（本地磁盘）和 `OssFileStorageServiceImpl`（阿里云 OSS）
- 静态资源映射：`WebConfig` 将 `/uploads/**` 映射到本地 `./uploads/` 目录
- **局限**：`storeFile()` 仅接受 `MultipartFile`，不支持直接传入 `byte[]`

## 目标流程

```
Python 脚本 (matplotlib)
  → base64 PNG 字符串 (JSON chart_image 字段)
    → PythonExecuteNode 解析
      → 解码 base64 → byte[]
      → 调用 FileStorageService 持久化
      → 获取 HTTP URL
      → SSE 流: <img src="{url}">
      → 传递给 ReportGeneratorNode（URL 替代 base64）
        → 嵌入 LLM prompt: ![chart]({url})
        → Markdown 报表中: ![chart]({url})
```

## 详细需求

### 1. FileStorageService 扩展

**文件：** `service/file/FileStorageService.java`

新增方法：

```java
/**
 * 存储字节数组为文件
 * @param data      文件字节内容
 * @param fileName  文件名（含扩展名，如 chart_xxx.png）
 * @param subPath   子目录（如 charts）
 * @return 文件访问 URL
 */
String storeFile(byte[] data, String fileName, String subPath);
```

两个实现类（`LocalFileStorageServiceImpl`、`OssFileStorageServiceImpl`）均需实现此方法。

### 2. PythonExecuteNode 图片持久化

**文件：** `workflow/node/PythonExecuteNode.java`

在解析到 `chart_image` 后的处理逻辑变更：

- 当前：直接将 base64 拼成 `<img src="data:image/png;base64,...">`
- 变更：
  1. 解码 base64 → `byte[]`
  2. 生成唯一文件名：`chart_{sessionId}_{timestamp}.png`
  3. 调用 `FileStorageService.storeFile(bytes, fileName, "charts")`
  4. 获取返回的 URL
  5. 将 URL 替换 base64 用于 SSE 输出和传递给下游节点

**容错处理：**
- 如果存储失败（如磁盘满、OSS 不可用），回退到 base64 内联方式，确保功能不中断
- 日志记录存储成功/失败

### 3. ReportGeneratorNode 图片引用

**文件：** `workflow/node/ReportGeneratorNode.java`

`buildAnalysisStepsAndData()` 方法中：

- 当前：`![chart](data:image/png;base64,...)`
- 变更：`![chart]({imageUrl})`

图片 URL 从 `PYTHON_EXECUTE_NODE_OUTPUT` 中获取，沿用已有的节点间数据传递机制。

### 4. SSE 流中图片展示

**PythonExecuteNode 的 SSE 输出：**

- 当前：`<img src="data:image/png;base64,...">`
- 变更：`<img src="{imageUrl}" alt="chart" style="max-width: 100%; height: auto;" />`

**Markdown 报表中的图片：**

- 当前：`![分析图表](data:image/png;base64,...)`
- 变更：`![分析图表]({imageUrl})`

URL 格式示例：
- 本地：`/uploads/data-agent/charts/chart_xxx.png`
- OSS：`https://{bucket}.{endpoint}/data-agent/charts/chart_xxx.png`

### 5. 图片清理策略（可选，后续迭代）

- 可基于会话过期时间自动清理旧图片
- 或提供手动清理 API

## 涉及文件

| 文件 | 变更类型 | 说明 |
|------|---------|------|
| `service/file/FileStorageService.java` | 修改 | 新增 `storeFile(byte[], String, String)` 方法 |
| `service/file/impls/LocalFileStorageServiceImpl.java` | 修改 | 实现新方法 |
| `service/file/impls/OssFileStorageServiceImpl.java` | 修改 | 实现新方法 |
| `workflow/node/PythonExecuteNode.java` | 修改 | 图片解码 + 持久化 + URL 替换 |
| `workflow/node/ReportGeneratorNode.java` | 修改 | 使用 URL 引用图片 |

## 验证标准

1. Python 生成 matplotlib 图表后，图片文件出现在 `{uploadPath}/charts/` 目录下
2. SSE 流中图片以 `<img src="{url}">` 形式输出，浏览器可正常加载
3. Markdown 报表中图片以 `![chart]({url})` 形式输出，Markdown 渲染器可正常显示
4. 当文件存储失败时，自动回退到 base64 内联方式，不影响用户体验
5. OSS 模式下图片 URL 为完整的 HTTPS 地址
