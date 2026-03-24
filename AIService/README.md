# AIService

AIService 是独立的 Python AI 编排服务，对 Java 后端暴露统一的 HTTP 接口。

当前版本已经从单轮对话升级为“两阶段对话 + Tool 路由”结构：

1. 第一轮先判断是否需要调用内部 Tool
2. 如果不需要 Tool，直接返回结果
3. 如果需要 Tool，先执行 Tool，再进入第二轮生成最终回答

同时，AIService 现在会输出“正文 + 结构化字段”两部分信息：

- 正文：`answer`
- 结构化字段：`intent`、`riskLevel`、`checklist`、`services`、`followUps`、`followUpQuestions`、`actionCards`、`disclaimer`

Java 后端会把这些结构化字段保存并透传给前端，前端不再从 `message.content` 里反向解析 JSON。

当前已接入的 Tool：

- `weight_analysis`
  - 根据宠物 ID 调 Java 后端内部接口读取最近体重记录
  - 结合体重记录做趋势分析
  - 将结果注入第二轮 Prompt，生成最终对用户可见的回答

后续已预留的 Tool 扩展位：

- `location_search`
- `service_lookup`
- `product_recommendation`

## 本地启动

```powershell
cd D:\pet\AIService
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
python run.py
```

## 主要接口

- `GET /health`
- `POST /api/ai/chat`

## 关键目录

- `ai_service/orchestrators`
  - 编排主流程
- `ai_service/capabilities`
  - 决策、Tool 执行、RAG、改写、安全等能力层
- `ai_service/tools`
  - 各类 Tool 的实现与注册表
- `ai_service/providers/backend`
  - Java 后端内部接口访问封装
- `ai_service/prompts/system`
  - 基础系统 Prompt、第一轮决策 Prompt、第二轮最终回答 Prompt
- `ai_service/prompts/tools`
  - Tool 注册表相关 Prompt

## 环境变量

参考 `.env.example`，重点包括：

- `BACKEND_BASE_URL`
- `BACKEND_TIMEOUT_SECONDS`
- `BASE_SYSTEM_PROMPT_FILE`
- `DECISION_PROMPT_FILE`
- `FINAL_RESPONSE_PROMPT_FILE`
- `TOOL_REGISTRY_PROMPT_FILE`
- `TOOL_ENABLED_LIST`
- `WEIGHT_ANALYSIS_LIMIT`
