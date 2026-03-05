这个是本地连接的示例，我现在的代码如果提供商选择qwen，是不支持的对吧
你现在新增一个供应商qwen-local,实现这个功能：支持向量模型Qwen3-Embedding-8B，对话模型：Qwen3-Coder-30B-A3B-Instruct

## Qwen3-Embedding-8B模型接口 
接口地址：http://10.132.200.185:30012/gateway/ti/qwen3-embedding/v1/embeddings
请求方式
POST

请求参数
• 请求头
| 参数名称       | 必选 | 类型   | 描述                                   |
|----------------|------|--------|----------------------------------------|
| Content-type   | 是   | String | 公共参数，本接口取值：application/json |
| szc-api-key    | 是   | String | 填入 对接前准备工作 中申请到的ApiKey   |


• 请求体
| 参数名称 | 必选 | 类型             | 描述               |
|----------|------|------------------|--------------------|
| model    | 是   | String           | 本接口取值：Qwen3-Embedding-8B |
| input    | 是   | Array of Message | 向量字符串列表     |


• 响应参数

| 参数名称       | 类型       | 描述                     |
|----------------|------------|--------------------------|
| Id             | String     | 唯一标识单次请求。       |
| object         | String     | 返回对象的类型           |
| created        | timestamp  | 本次请求时间戳           |
| model          | String     | 生成响应所使用的模型名称或标识符 |
| data           | Object     | 返回的内容               |
| data.index     | integer    | 索引                     |
| data.object    | String     | 对象类型                 |
| data.embedding | Array      | 向量化数组               |
| usage          | Object     | 使用方式                 |
 
示例
请求：
```bash
curl -i -X POST \
-H "Content-Type:application/json" \
-H "szc-api-key:*********" \
-d '{"model" : "Qwen3-Embedding-8B","input": ["The capital of Brazil is Brasilia.","The capital of France is Paris."]}' \
http://10.132.200.185:30012/gateway/ti/qwen3-embedding/v1/embeddings
```

返回：
```bash
 {
    "id": "embd-1d2d641a2a568d506f0f31f82e21d9a6",
    "object": "list",
    "created": 1755750790,
    "model": "Qwen3-Embedding-8B",
    "data": [
        {
            "index": 0,
            "object": "embedding",
            "embedding": [
                -0.04193115234375,
                0.01457977294921875,
                -0.0264892578125,
                0.034698486328125,
                -0.0260772705078125,
                -0.004848480224609375,
                -0.033203125,
                -0.0307159423828125,
                -0.014251708984375,
                -0.0214996337890625,
                0.0307159423828125,
                0.01470184326171875,
                0.0162506103515625,
                -0.01206207275390625,
                -0.0005807876586914062,
                0.056549072265625,
                0.01430511474609375,
                ............
            ]
        },
        {
            "index": 2,
            "object": "embedding",
            "embedding": [
                0.0227813720703125,
                -0.01001739501953125,
                0.01131439208984375,
                0.044830322265625,
                0.007381439208984375,
                0.014678955078125,
                -0.011199951171875,
                -0.032135009765625,
                0.02081298828125,
                -0.025543212890625,
                -0.041656494140625,
                0.01953125,
                -0.003772735595703125,
                0.0292816162109375,
                -0.0137939453125,
                ............
            ]
        }
    ],
    "usage": {
        "prompt_tokens": 38,
        "total_tokens": 38,
        "completion_tokens": 0,
        "prompt_tokens_details": null
    }
}
```

## 对话模型：Qwen3-Coder-30B-A3B-Instruct接口
接口地址
http://10.132.200.185:30012/gateway/ti/qwen3-coder-30b/v1/chat/completions

请求方式
POST

请求参数
• 请求头
| 参数名称       | 必选 | 类型   | 描述                                   |
|----------------|------|--------|----------------------------------------|
| Content-type   | 是   | String | 公共参数，本接口取值：application/json |
| szc-api-key    | 是   | String | 填入 对接前准备工作 中申请到的ApiKey   |

• 请求体
| 参数名称        | 必选 | 类型              | 描述                                                                 |
|-----------------|------|-------------------|----------------------------------------------------------------------|
| model           | 否   | String            | 本接口取值：Qwen3-Coder-30B                                          |
| messages.N      | 是   | Array of Message  | 会话列表 示例值：[{"role":"user","content":"你好"}]                  |
| message.Role    | 是   | String            | role表示角色 user标识用户提问，assistant标识返回的答案； 示例值：user; assistant |
| message.Content | 是   | String            | 对话内容，示例值：你好                                               |
| stream          | 否   | Boolean           | 是否流式输出 示例值：true                                            |
| max_tokens      | 否   | Int               | 控制模型生成过程中考虑的词汇（token）范围，只从概率最高的k个候选词中选择。 |
| top_p           | 否   | Float             | 控制模型生成过程中考虑的词汇范围，使用累计概率选择候选词，直到累计概率超过给定的阈值。 |
| temperature     | 否   | Float             | 控制生成的随机性，较高的值会产生更多样化的输出。                       |                                

• 响应参数

| 参数名称               | 类型              | 描述                                                                 |
|------------------------|-------------------|----------------------------------------------------------------------|
| Id                     | String            | 唯一标识单次请求。                                                   |
| object                 | String            | 返回对象的类型                                                       |
| created                | timestamp         | 本次请求时间戳                                                       |
| Choices                | Array of Choice   | 会话列表                                                             |
| model                  | String            | 生成响应所使用的模型名称或标识符                                     |
| Choice.FinishReason    | String            | 结束原因，stop标识请求结束，用于流式请求停止判断                     |
| Choice.Index           | Int64             | 索引，默认为0                                                        |
| Choice.Message         | Message           | 完整内容, 非流式请求输出该参数                                       |
| Choice.Delta           | Message           | 增量内容，流式请求输出该参数                                         |
| Message.Role           | String            | role表示角色 user标识用户提问，assistant标识返回的答案；<br>示例值：user; assistant |
| Message.Content        | String            | 对话内容，示例值：你好                                               |

示例
• 示例 对话，流式
请求：
```bash
curl -X POST \
-H "Content-Type:application/json" \
-H "szc-api-key:***********" \
-d '{"model":"Qwen3-Coder-30B","messages":[{"role":"user","content":"你是谁"}],"stream":true}' \
http://10.132.200.185:30012/gateway/ti/qwen3-coder-30b/v1/chat/completions
```
返回：
```bash
{
  "id": "chatcmpl-7e1b8a6881a74b21be691738bac67e2a",
  "object": "chat.completion",
  "created": 1765953819,
  "model": "Qwen3-Coder-30B",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "To square the number 1024, I need to calculate 1024².\n\n1024² = 1024 × 1024\n\nLet me calculate this:\n1024 × 1024 = 1,048,576\n\nTherefore, 1024 squared equals 1,048,576.",
        "refusal": null,
        "annotations": null,
        "audio": null,
        "function_call": null,
        "tool_calls": [],
        "reasoning_content": null
      },
      "logprobs": null,
      "finish_reason": "stop",
      "stop_reason": null
    }
  ],
  "service_tier": null,
  "system_fingerprint": null,
  "usage": {
    "prompt_tokens": 16,
    "total_tokens": 103,
    "completion_tokens": 87,
    "prompt_tokens_details": null
  },
  "prompt_logprobs": null,
  "kv_transfer_params": null
}
```
• 示例2 对话，流式
请求：
```bash
curl -X POST \
-H "Content-Type:application/json" \
-H "szc-api-key:***********" \
-d '{"model":"Qwen3-Coder-30B","messages":[{"role":"user","content":"你是谁"}],"stream":true}' \
http://10.132.200.185:30012/gateway/ti/qwen3-coder-30b/v1/chat/completions
```

返回：
```bash
data: {"id":"chatcmpl-9b11af4b1f044e63a4b65c4ce3351ec0","object":"chat.completion.chunk","created":1765952966,"model":"Qwen3-Coder-30B","choices":[{"index":0,"delta":{"content":"5"},"logprobs":null,"finish_reason":null}]}

data: {"id":"chatcmpl-9b11af4b1f044e63a4b65c4ce3351ec0","object":"chat.completion.chunk","created":1765952966,"model":"Qwen3-Coder-30B","choices":[{"index":0,"delta":{"content":"7"},"logprobs":null,"finish_reason":null}]}

data: {"id":"chatcmpl-9b11af4b1f044e63a4b65c4ce3351ec0","object":"chat.completion.chunk","created":1765952966,"model":"Qwen3-Coder-30B","choices":[{"index":0,"delta":{"content":"6"},"logprobs":null,"finish_reason":null}]}

data: {"id":"chatcmpl-9b11af4b1f044e63a4b65c4ce3351ec0","object":"chat.completion.chunk","created":1765952966,"model":"Qwen3-Coder-30B","choices":[{"index":0,"delta":{"content":"."},"logprobs":null,"finish_reason":null}]}

data: {"id":"chatcmpl-9b11af4b1f044e63a4b65c4ce3351ec0","object":"chat.completion.chunk","created":1765952966,"model":"Qwen3-Coder-30B","choices":[{"index":0,"delta":{"content":""},"logprobs":null,"finish_reason":"stop","stop_reason":null}]}

data: [DONE]

```
