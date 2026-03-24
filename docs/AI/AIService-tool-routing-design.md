# AIService Tool 路由与两阶段对话设计

## 目标

本次改造把 AIService 从“单轮统一回答”升级为“两阶段对话 + Tool 路由”架构，先支撑体重分析 Tool，再为后续地点查询、服务查询、用品推荐等 Tool 预留统一扩展位。

## 第一阶段：工具决策

第一轮内部对话不直接负责复杂分析，而是先判断：

1. 当前问题是否需要调用 Tool
2. 如果需要，应该调用哪个 Tool
3. Tool 缺不缺必要参数
4. 如果不需要 Tool，是否可以直接返回最终回答

第一轮输出统一为 `ToolDecision` 结构，包含：

- `needTool`
- `toolName`
- `toolInput`
- `answer`
- `riskLevel`
- `checklist`
- `services`
- `followUps`
- `disclaimer`

当 `needTool = false` 时，第一轮结果直接作为最终回答返回。

当 `needTool = true` 时，系统执行指定 Tool，然后进入第二轮最终回答。

## 第二阶段：最终回答

第二轮在拿到 Tool 结果后，结合：

- 用户问题
- 宠物信息
- 最近对话
- RAG 上下文
- Tool 结果

生成最终 JSON 回答。

当前最终回答除了 `answer` 之外，还会一起返回结构化展示字段，例如：

- `intent`
- `riskLevel`
- `checklist`
- `services`
- `followUps`
- `followUpQuestions`
- `actionCards`
- `disclaimer`

这样 Java 后端可以直接把结构化字段透传给前端，前端不再需要从 `content` 里反向解析 JSON。

## Prompt 目录

AIService 内部 Prompt 目录调整为：

- `ai_service/prompts/system/base_system_prompt.md`
  - 全局角色、安全边界、最终输出格式
- `ai_service/prompts/system/decision_prompt.md`
  - 第一轮 Tool 决策规则
- `ai_service/prompts/system/final_response_prompt.md`
  - 第二轮最终回答规则
- `ai_service/prompts/tools/tool_registry_prompt.md`
  - Tool 注册表的静态说明

其中第一轮真正使用的 Tool 列表，不只来自静态 Markdown，还会由 `ToolRegistry` 在运行时把当前启用的 Tool 定义拼接进 prompt，避免提示词和代码注册表脱节。

## Tool 组织形式

Tool 代码按“目录化 Tool”组织，每个 Tool 独立管理：

- `tool.py`
  - Tool 执行入口
- `schemas.py`
  - Tool 输入输出结构
- `analyzer.py`
  - 规则分析或业务逻辑
- `provider.py` / `backend_provider.py`
  - 下游依赖访问

当前已落地的 Tool：

- `ai_service/tools/weight_analysis/`

后续建议按相同模式新增：

- `ai_service/tools/location_search/`
- `ai_service/tools/service_lookup/`
- `ai_service/tools/product_recommendation/`

## Tool 注册表

`ai_service/tools/registry.py` 负责维护所有 Tool 的元信息：

- 名称
- 用途
- 适用场景
- 必需输入
- 不应调用的场景
- Tool 实例
- 是否启用

这样新增 Tool 时，只需要：

1. 新建 Tool 目录
2. 在 `registry.py` 中注册
3. 在配置里启用

而不需要修改第一轮决策主流程。

## 体重分析 Tool 当前流程

1. 第一轮判断用户问题是否涉及体重趋势分析
2. 若命中且具备 `userId + petId`，选择 `weight_analysis`
3. Tool 调用 Java 后端内部接口：
   - `GET /internal/ai/pets/{petId}/weight-records`
4. Tool 内部 `WeightAnalyzer` 基于最近记录做保守趋势分析
5. Tool 输出结构化结果
6. 第二轮把 Tool 结果整合为最终面向用户的回答

## 相关代码落点

本次改造重点文件：

- `ai_service/capabilities/decision_service.py`
- `ai_service/capabilities/tool_service.py`
- `ai_service/prompts/prompt_builder.py`
- `ai_service/tools/registry.py`
- `ai_service/tools/weight_analysis/`
- `ai_service/providers/backend/pet_weight_provider.py`
- `ai_service/orchestrators/chat_orchestrator.py`

Java 后端配套改动：

- `backend/src/main/java/com/pet/service/ai/AiServiceProvider.java`
- `backend/src/main/java/com/pet/service/AiChatService.java`
- `backend/src/main/java/com/pet/api/ai/dto/AiChatMessageResponse.java`
- `backend/src/main/resources/db/migration/V20260324195000__add_ai_chat_message_structured_payload.sql`

## 配置项

新增或调整的关键配置：

- `BASE_SYSTEM_PROMPT_FILE`
- `DECISION_PROMPT_FILE`
- `FINAL_RESPONSE_PROMPT_FILE`
- `TOOL_REGISTRY_PROMPT_FILE`
- `TOOL_ENABLED_LIST`
- `WEIGHT_ANALYSIS_LIMIT`

默认只启用：

- `weight_analysis`
