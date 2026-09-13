以下是 DeepSeek Responses API 文档的 Markdown 格式内容：

---

# 使用 Responses API

支持的模型：Responses API 目前仅支持 `deepseek-v4-flash` 模型，暂不支持 `deepseek-v4-pro` 模型。我们将于 2026 年 8 月初增加对 `deepseek-v4-pro` 模型的支持。

为了满足大家对 Codex 的需求，我们的 API 新增了对 Responses API 格式的支持，其 `base_url` 为 [https://api.deepseek.com](https://api.deepseek.com)。通过简单的配置，即可在 Codex 中使用 DeepSeek 模型。

## 将 DeepSeek 模型接入 Codex

请参考[接入 Codex](https://api-docs.deepseek.com/zh-cn/quick_start/agent_integrations/codex)。

## 通过 Responses API 调用 DeepSeek 模型

```python
# Please install OpenAI SDK first: `pip3 install openai`
from openai import OpenAI

client = OpenAI(
    api_key="",
    base_url="https://api.deepseek.com"
)

response = client.responses.create(
    model="deepseek-v4-flash",
    instructions="You are a helpful assistant.",
    input="Hi, how are you?",
)

print(response.output_text)
```


## 流式输出

设置 `stream: true`，响应将以语义化的流式 SSE 事件序列返回。每个事件带有表示事件类型的 `event` 字段和递增的 `sequence_number`。流以 `response.completed` / `response.incomplete` / `response.failed` 事件结束，没有 `data: [DONE]` 消息。

```python
stream = client.responses.create(
    model="deepseek-v4-flash",
    instructions="You are a helpful assistant.",
    input="Hi, how are you?",
    stream=True,
)

for event in stream:
    if event.type == "response.output_text.delta":
        print(event.delta, end="")
```


### 完整事件列表

| 事件                                                         | 说明                                                         |
| ------------------------------------------------------------ | ------------------------------------------------------------ |
| `response.created`                                           | 首个事件；响应已创建，状态为 in_progress                     |
| `response.in_progress`                                       | 响应正在生成中                                               |
| `response.output_item.added` / `response.output_item.done`   | 一个输出 item（reasoning / message / function_call / custom_tool_call / web_search_call）开始 / 完成 |
| `response.content_part.added` / `response.content_part.done` | 输出 item 中的一个内容块开始 / 完成                          |
| `response.reasoning_text.delta` / `response.reasoning_text.done` | 思维链文本增量 / 完整思维链文本                              |
| `response.output_text.delta` / `response.output_text.done`   | 输出文本增量 / 完整输出文本                                  |
| `response.function_call_arguments.delta` / `response.function_call_arguments.done` | Function 调用参数增量 / 完整参数                             |
| `response.custom_tool_call_input.delta` / `response.custom_tool_call_input.done` | Custom 工具调用（apply_patch）输入增量 / 完整输入            |
| `response.web_search_call.in_progress` / `response.web_search_call.searching` / `response.web_search_call.completed` | 服务端联网搜索工具调用的状态更新                             |
| `response.completed`                                         | 响应正常完成时的最后一个事件，携带包含 usage 的完整 response 对象 |
| `response.incomplete`                                        | 响应被截断（如达到 max_output_tokens）时的最后一个事件，携带完整 response 对象 |
| `response.failed`                                            | 响应失败时的最后一个事件，携带含 error 详情的完整 response 对象 |



## 兼容性明细

本小节罗列了 DeepSeek API 对 Responses API 的兼容性细节。Responses API 完整格式定义，请参考 [OpenAI 官方 API 手册](https://developers.openai.com/api/reference/resources/responses/methods/create)。

### 顶层请求参数

| 参数                                          | 支持情况                                                     |
| --------------------------------------------- | ------------------------------------------------------------ |
| `model`                                       | 支持。目前仅支持 `deepseek-v4-flash`（暂不支持 `deepseek-v4-pro`），见[模型 & 价格](https://api-docs.deepseek.com/zh-cn/quick_start/pricing) |
| `input`                                       | 支持。字符串或输入 item 列表；`input` 与 `instructions` 至少传一个 |
| `instructions`                                | 支持。作为第一条 system 消息                                 |
| `stream`                                      | 支持                                                         |
| `temperature`                                 | 支持（范围 [0.0, 2.0]；思考模式下不生效）                    |
| `top_p`                                       | 支持（思考模式下不生效）                                     |
| `max_output_tokens`                           | 支持                                                         |
| `top_logprobs`                                | 支持（范围 [0, 20]）                                         |
| `tools`                                       | 部分支持。`function` / `web_search` 支持；其他类型忽略，见下方 Tools 表 |
| `tool_choice`                                 | 支持。`none` / `auto` / `required` / 指定某个工具（`{"type": "function", "name": ...}` 或 `{"type": "web_search"}` / `{"type": "web_search_2025_08_26"}`） |
| `reasoning`                                   | 部分支持。`effort` 支持；`summary` 可传入但不生成摘要        |
| `text`                                        | 部分支持。`format` 完整支持；`verbosity` 可传入但不生效      |
| `user`                                        | 支持。参考[限速与用户隔离](https://api-docs.deepseek.com/zh-cn/quick_start/rate_limit) |
| `parallel_tool_calls`                         | 忽略（并行工具调用始终开启）                                 |
| `max_tool_calls`                              | 忽略                                                         |
| `previous_response_id`                        | 不支持（无状态 API）                                         |
| `conversation`                                | 不支持（无状态 API）                                         |
| `store`                                       | 不支持。响应中恒为 `store: false`                            |
| `background`                                  | 不支持                                                       |
| `metadata`                                    | 不支持                                                       |
| `include`                                     | 不支持                                                       |
| `prompt`                                      | 不支持                                                       |
| `truncation`                                  | 不支持。输入超出上下文窗口时返回 400 错误                    |
| `service_tier`                                | 不支持                                                       |
| `safety_identifier`                           | 不支持                                                       |
| `prompt_cache_key` / `prompt_cache_retention` | 不支持。上下文缓存自动管理，见[上下文硬盘缓存](https://api-docs.deepseek.com/zh-cn/guides/kv_cache) |
| `context_management`                          | 不支持                                                       |
| `stream_options`                              | 不支持                                                       |

不支持的参数会被静默忽略、不会报错，因此现有的 Responses API 客户端无需修改即可接入。

### 输入 Items

| 类型                   | 支持情况                                                     |
| ---------------------- | ------------------------------------------------------------ |
| `message`              | 支持。角色支持 `user` / `assistant` / `system` / `developer`（`developer` 视同 `system`）；`content` 支持字符串和 `input_text` / `output_text` 内容块。不支持图片、文件输入（`input_image` 内容块不会报错，但会被替换为占位文本） |
| `function_call`        | 支持。归并到相邻 assistant 消息                              |
| `function_call_output` | 支持                                                         |
| `reasoning`            | 支持。明文 `content` 归并到相邻 assistant 消息；`summary`、`encrypted_content` 不支持 |
| `web_search_call`      | 支持。原样回传即可，服务端自动恢复搜索结果                   |
| 其他类型               | 忽略                                                         |

### Tools

| 类型                                                         | 支持情况                                                     |
| ------------------------------------------------------------ | ------------------------------------------------------------ |
| `function`                                                   | 支持                                                         |
| `web_search` / `web_search_2025_08_26`                       | 支持，服务端执行。`search_context_size`、`user_location` 忽略 |
| `custom`                                                     | 仅支持 `{"type": "custom", "name": "apply_patch"}`（用于 Codex 兼容）；其他名称返回 400 错误 |
| `file_search` / `code_interpreter` / `computer_use` / `mcp` 等其他内置工具 | 忽略                                                         |

### 响应字段

响应对象与 OpenAI Responses API 的 response 结构兼容。依赖未支持能力的字段恒为固定值（如 `store: false`、`previous_response_id: null`、`parallel_tool_calls: true`）。

Token 用量在 `usage` 中返回：

- `input_tokens`：输入 token 数，其中 `input_tokens_details.cached_tokens` 为命中[上下文缓存](https://api-docs.deepseek.com/zh-cn/guides/kv_cache)的 token 数
- `output_tokens`：输出 token 数，其中 `output_tokens_details.reasoning_tokens` 为思维链 token 数

--------------

你说的 **Responses API 参数文档** 应该指 OpenAI 的 **Responses API（`/v1/responses`）**，下面整理一份Openai 官方开发视角的参数说明，包含：

* 基础请求参数
* 多轮上下文
* 工具调用（Tools）
* Web Search
* File Search
* Function Calling
* Structured Output
* Streaming
* 常用返回字段

---

# Responses API 请求

Endpoint:

```
POST https://api.openai.com/v1/responses
```

Header:

```http
Authorization: Bearer $OPENAI_API_KEY
Content-Type: application/json
```

---

# 1. 基础参数

## model

模型名称（必填）

```json
{
  "model": "gpt-5-mini"
}
```

示例：

| 模型         | 用途     |
| ---------- | ------ |
| gpt-5.5    | 复杂推理   |
| gpt-5-mini | 低成本通用  |
| gpt-4.1    | 代码、多模态 |
| o3         | 推理     |

---

# input

输入内容。

支持：

* string
* message 数组
* 多模态内容

## 简单文本

```json
{
 "input":"介绍一下量子计算"
}
```

---

## message 格式

类似 Chat Completions：

```json
{
 "input":[
   {
    "role":"user",
    "content":"你好"
   }
 ]
}
```

role:

| role      | 说明  |
| --------- | --- |
| user      | 用户  |
| assistant | 模型  |
| system    | 系统  |
| developer | 开发者 |

---

# instructions

系统提示词。

等价：

Chat API:

```
system message
```

示例：

```json
{
"instructions":
"你是一个专业小说编辑"
}
```

---

# 2. 输出控制

---

# max_output_tokens

最大输出 token

```json
{
"max_output_tokens":2000
}
```

---

# temperature

随机性

范围：

```
0-2
```

例如：

```json
{
"temperature":0.7
}
```

---

# top_p

核采样

```json
{
"top_p":0.9
}
```

一般不要同时设置：

```
temperature
top_p
```

---

# 3. 多轮上下文

Responses API 推荐：

## previous_response_id

继续之前响应。

第一次：

```json
{
 "model":"gpt-5-mini",
 "input":"你好"
}
```

返回：

```json
{
"id":"resp_abc123"
}
```

下一次：

```json
{
 "model":"gpt-5-mini",
 "previous_response_id":"resp_abc123",
 "input":"继续"
}
```

类似：

```
chat history 自动维护
```

---

# 4. tools 工具系统

格式：

```json
{
 "tools":[]
}
```

工具类型：

| tool        | 作用    |
| ----------- | ----- |
| web_search  | 联网搜索  |
| file_search | 文件检索  |
| computer    | 电脑操作  |
| function    | 自定义函数 |

---

# Web Search

## 开启

```json
{
"tools":[
 {
  "type":"web_search"
 }
]
}
```

模型会自动决定：

```
是否搜索
搜索什么
引用哪些结果
```

---

# Web Search 完整参数

```json
{
"tools":[
{
"type":"web_search",
"search_context_size":"medium"
}
]
}
```

---

## search_context_size

控制搜索上下文数量

可选：

```
low
medium
high
```

| 值      | 说明      |
| ------ | ------- |
| low    | 少量结果，便宜 |
| medium | 默认      |
| high   | 深度搜索    |

---

## user_location

影响本地搜索结果

```json
{
"type":"web_search",
"user_location":{
 "type":"approximate",
 "country":"JP",
 "city":"Tokyo"
}
}
```

字段：

```json
{
"type":"approximate",
"country":"US",
"city":"New York",
"region":"NY",
"timezone":"America/New_York"
}
```

用途：

例如：

```
附近餐厅
天气
商店
价格
```

---

# Web Search 示例

请求：

```json
{
 "model":"gpt-5-mini",

 "input":
 "2026年东京天气",

 "tools":[
  {
   "type":"web_search"
  }
 ]
}
```

模型输出：

```json
{
"type":"web_search_call",
"status":"completed"
}
```

然后：

```
message
 + citations
```

---

# 5. Function Calling

自定义工具。

例如：

查询天气：

```json
{
"tools":[
{
"type":"function",

"name":"get_weather",

"description":
"查询天气",

"parameters":{
"type":"object",

"properties":{
 "city":{
  "type":"string"
 }
},

"required":[
"city"
]
}

}
]
}
```

模型输出：

```json
{
"type":"function_call",

"name":"get_weather",

"arguments":
"{\"city\":\"Tokyo\"}"
}
```

你的服务器执行：

返回：

```json
{
"type":"function_call_output",

"call_id":"xxx",

"output":
"东京 25℃"
}
```

---

# 6. File Search

知识库检索。

需要 vector store。

开启：

```json
{
"tools":[
{
"type":"file_search",

"vector_store_ids":[
"vs_xxxxx"
]

}
]
}
```

适合：

* 小说库
* 文档
* PDF
* 企业知识库

---

# 7. Structured Output

让模型输出固定 JSON。

参数：

```json
{
"text":{
 "format":{
  "type":"json_schema",

  "name":"book",

  "schema":{
   "type":"object",
   "properties":{
    "title":{
     "type":"string"
    }
   }
  }
 }
}
}
```

返回：

```json
{
"title":"三体"
}
```

---

# 8. Streaming

开启：

```json
{
"stream":true
}
```

返回：

SSE:

```
event: response.output_text.delta

data:
{
"delta":"你好"
}
```

事件：

| event                      | 说明   |
| -------------------------- | ---- |
| response.created           | 创建   |
| response.output_text.delta | 文本增量 |
| response.completed         | 完成   |
| response.error             | 错误   |

---

# 9. 多模态输入

图片：

```json
{
"input":[
{
"role":"user",
"content":[
{
"type":"input_text",
"text":"描述图片"
},
{
"type":"input_image",
"image_url":"https://xxx/a.png"
}
]
}
]
}
```

---

# 10. 返回结构

Response:

```json
{
"id":"resp_xxx",

"status":"completed",

"output":[

{
"type":"message",

"role":"assistant",

"content":[

{
"type":"output_text",

"text":"回答内容"
}

]

}

]
}
```

---

# 11. Usage

token统计：

```json
{
"usage":{

"input_tokens":1000,

"output_tokens":500,

"total_tokens":1500

}
}
```

注意：

```
usage.prompt_tokens
```

是 Chat Completions 字段。

Responses:

使用：

```
usage.input_tokens
usage.output_tokens
```

---

# 12. 推荐配置（实际项目）

## 普通聊天

```json
{
"model":"gpt-5-mini",

"instructions":
"你是助手",

"input":"你好",

"max_output_tokens":2048
}
```

---

## AI小说助手（你的场景）

建议：

```json
{
"model":"gpt-5-mini",

"input":[
 {
 "role":"user",
 "content":"继续写下一章"
 }
],

"tools":[
 {
  "type":"file_search",
  "vector_store_ids":[
   "小说知识库"
  ]
 },
 {
  "type":"web_search"
 }
],

"temperature":0.8
}
```

架构：

```
用户输入
   |
Responses API
   |
   +-- file_search
   |       |
   |       小说大纲/人物
   |
   +-- web_search
   |       |
   |       现实资料
   |
   +-- GPT
           |
        输出
```

---

# 与 Chat Completions 对比

| 功能    | Chat API  | Responses API        |
| ----- | --------- | -------------------- |
| 聊天    | ✅         | ✅                    |
| 多轮    | 手动history | previous_response_id |
| 工具    | functions | tools                |
| 联网    | 无         | web_search           |
| 文件    | 无         | file_search          |
| 多模态   | 有限        | 原生                   |
| Agent | 弱         | 强                    |

---

对于你之前提到的 **安卓小说阅读器 + AI维护大纲 + 本地小说搜索**，Responses API 推荐组合：

```
Responses API
+
previous_response_id
+
file_search(小说资料)
+
function calling(修改大纲/保存章节)
+
web_search(资料查询)
+
structured output(JSON大纲)
```

基本就是目前 OpenAI Agent 架构的标准用法。
