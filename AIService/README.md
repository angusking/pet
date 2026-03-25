# AIService

AIService 是独立的 Python AI 编排服务，对 Java 后端暴露统一的 HTTP 接口。

当前版本已经从单轮对话升级为“两阶段对话 + Tool 路由”结构：

1. 第一轮先判断是否需要调用内部 Tool。
2. 如果不需要 Tool，直接返回结果。
3. 如果需要 Tool，先执行 Tool，再进入第二轮生成最终回答。

同时，AIService 现在会输出“正文 + 结构化字段”两部分信息：

- 正文：`answer`
- 结构化字段：`followUp`、`intent`、`riskLevel`、`checklist`、`services`、`followUps`、`followUpQuestions`、`actionCards`、`disclaimer`

Java 后端会把这些结构化字段单独保存并透传给前端，前端不再需要从 `message.content` 里反向解析 JSON。

## 当前已接入的 Tool

- `weight_analysis`
  - 根据宠物 ID 调 Java 后端内部接口读取最近体重记录
  - Tool 内部先整理原始记录
  - 再将整理后的上下文发送给 LLM 做趋势分析
  - 最后把分析结果注入第二轮 Prompt，生成最终用户可见回答

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
  - 聊天主编排流程
- `ai_service/capabilities`
  - 决策、Tool 执行、RAG、改写、安全等能力层
- `ai_service/tools`
  - 各类 Tool 的实现与注册表
- `ai_service/providers/backend`
  - Java 后端内部接口访问封装
- `ai_service/prompts/system`
  - 基础系统 Prompt、第一轮决策 Prompt、第二轮最终回答 Prompt
- `ai_service/prompts/tools`
  - Tool 注册表 Prompt 和 Tool 内部专用 Prompt

## 体重分析链路说明

当前体重分析链路采用“后端只取数，AIService 内部分析”的职责拆分：

1. Java 后端内部接口 `/internal/ai/pets/{petId}/weight-records`
   - 只返回宠物基础信息和原始体重记录
   - 不提前做趋势判断和解释文案
2. `weight_analysis` Tool
   - 拉取原始记录
   - 构造稳定的分析上下文
   - 调用专用 LLM Prompt 生成趋势分析结果
3. 第二轮最终回答
   - 消费 Tool 分析结果
   - 组织成最终结构化回答

这样后续如果要调整体重分析策略，只需要改 AIService，不需要改 Java 查询接口。

## 环境变量

参考 `.env.example`，重点包括：

- `BACKEND_BASE_URL`
- `BACKEND_TIMEOUT_SECONDS`
- `BASE_SYSTEM_PROMPT_FILE`
- `DECISION_PROMPT_FILE`
- `FINAL_RESPONSE_PROMPT_FILE`
- `TOOL_REGISTRY_PROMPT_FILE`
- `WEIGHT_ANALYSIS_TOOL_PROMPT_FILE`
- `TOOL_ENABLED_LIST`
- `WEIGHT_ANALYSIS_LIMIT`

## 日志

默认会写三类日志：

1. 应用级日志
   - `AIlog/application.log`

2. 单次 AI 请求与 LLM 交互日志
   - `AIlog/YYYY-MM-DD/*_send.txt`
   - `AIlog/YYYY-MM-DD/*_decision_llm.txt`
   - `AIlog/YYYY-MM-DD/*_final_llm.txt`
   - `AIlog/YYYY-MM-DD/*_weight_analysis_tool_llm.txt`
   - 对应的 `*_error.txt`

3. Java 后端与 AIService 的交互日志
   - `backend/logs/backend/ai-interaction.log`
