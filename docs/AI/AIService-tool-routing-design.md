# AIService Tool 路由与两阶段对话设计

## 目标

本次改造把 AIService 从“单轮统一回答”升级为“第一轮决策 + Tool 执行 + 第二轮最终回答”的两阶段架构。

当前先落地 `weight_analysis`，但整体结构按多 Tool 扩展设计，后续可以平滑接入：

- `location_search`
- `service_lookup`
- `product_recommendation`

## 总体流程

整体链路分为 4 步：

1. 用户问题进入 AIService。
2. 第一轮对话只负责决策：
   - 是否需要调用 Tool
   - 需要调用哪个 Tool
   - 当前输入是否满足 Tool 的必要参数
3. 如果需要 Tool，先执行 Tool。
4. Tool 结果被注入第二轮 Prompt，再生成最终返回给用户的结构化回答。

简化后的时序如下：

```text
用户问题
  -> 第一轮决策
    -> 不需要 Tool：直接返回最终回答
    -> 需要 Tool：执行 Tool
      -> 将 Tool 结果注入第二轮 Prompt
      -> 生成最终回答
```

## 第一轮：Tool 决策

第一轮不直接做复杂分析，职责非常单一：

- 判断是否需要调用 Tool
- 选择最相关的一个 Tool
- 如果缺少必要参数，直接提示缺什么信息
- 如果无需 Tool，直接给出最终回答

第一轮输出统一使用 `ToolDecision` 结构，包含：

- `needTool`
- `toolName`
- `toolInput`
- `followUp`
- `intent`
- `answer`
- `riskLevel`
- `checklist`
- `services`
- `followUps`
- `followUpQuestions`
- `actionCards`
- `disclaimer`

当 `needTool = false` 时，第一轮结果可以直接作为最终回复返回。

当 `needTool = true` 时，系统会继续执行对应 Tool，再进入第二轮。

## 第二轮：最终回答

第二轮负责把以下信息整合成最终输出：

- 用户当前问题
- 宠物信息
- 最近对话
- RAG 上下文
- Tool 分析结果

最终回答仍然要求输出结构化 JSON，核心字段包括：

- `followUp`
- `intent`
- `answer`
- `riskLevel`
- `checklist`
- `services`
- `followUps`
- `followUpQuestions`
- `actionCards`
- `disclaimer`

Java 后端会把这些结构化字段单独保存并透传给前端，前端不再依赖从 `message.content` 中反向解析 JSON。

## Prompt 分层

Prompt 目录按职责拆分：

- `ai_service/prompts/system/base_system_prompt.md`
  - 全局角色、安全边界、输出总规则
- `ai_service/prompts/system/decision_prompt.md`
  - 第一轮 Tool 决策规则
- `ai_service/prompts/system/final_response_prompt.md`
  - 第二轮最终回答规则
- `ai_service/prompts/tools/tool_registry_prompt.md`
  - 可用 Tool 列表和通用选择规则
- `ai_service/prompts/tools/weight_analysis_llm_prompt.md`
  - 体重分析 Tool 内部给 LLM 的专用分析提示词

这层拆分的目的，是避免把“工具选择”“工具内部分析”“最终用户回答”混在同一个 Prompt 里。

## Tool 组织形式

每个 Tool 都按目录化方式组织，避免后续扩展时继续堆单文件逻辑。

当前体重分析 Tool 的结构如下：

```text
ai_service/tools/weight_analysis/
├─ tool.py
├─ schemas.py
├─ context_builder.py
└─ llm_analyzer.py
```

各文件职责：

- `tool.py`
  - Tool 对外入口
  - 负责串联“查后端 -> 整理上下文 -> 调 LLM 分析”
- `schemas.py`
  - Tool 输入输出结构
- `context_builder.py`
  - 把后端原始记录整理成稳定的分析上下文
- `llm_analyzer.py`
  - 调用 LLM 完成体重趋势分析

## Tool Registry

`ai_service/tools/registry.py` 负责集中注册所有 Tool 元信息，包括：

- `name`
- `description`
- `when_to_use`
- `required_inputs`
- `when_not_to_use`
- `notes`
- `enabled`
- `tool`

第一轮决策 Prompt 使用的 Tool 列表，不只来自静态 Markdown，还会在运行时把注册表中当前启用的 Tool 信息拼进去，避免 Prompt 文案和代码注册表逐渐漂移。

## 体重分析 Tool 的职责边界

### Java 后端职责

`GET /internal/ai/pets/{petId}/weight-records`

后端现在只负责：

- 校验 `userId + petId` 归属
- 返回宠物基础信息
- 返回最近 N 条原始体重记录

后端不再负责：

- 趋势判断
- 分类参考解释
- 面向 AI 的分析文案
- “上一条变化值”等分析型派生结论

### AIService Tool 职责

`weight_analysis` Tool 现在负责：

1. 调 Java 内部接口获取原始体重记录
2. 使用 `context_builder` 整理成稳定上下文
3. 调用专用 LLM Prompt 完成体重趋势分析
4. 将分析结果返回给第二轮最终回答

也就是说，当前体重分析链路是：

```text
Java 后端：只取数
AIService Tool：整理数据 + 调 LLM 分析
第二轮回答：消费 Tool 分析结果并生成用户可见回复
```

## 体重分析 Tool 当前流程

1. 第一轮判断用户问题是否涉及体重趋势分析。
2. 如果命中且具备 `userId + petId`，选择 `weight_analysis`。
3. Tool 调用 Java 内部接口：
   - `GET /internal/ai/pets/{petId}/weight-records`
4. Tool 将返回的原始记录整理为稳定上下文。
5. Tool 内部使用专用 Prompt 调 LLM 做趋势分析。
6. Tool 输出结构化分析结果。
7. 第二轮把该分析结果整合为最终面向用户的回答。

## 相关代码落点

AIService 关键文件：

- `ai_service/capabilities/decision_service.py`
- `ai_service/capabilities/tool_service.py`
- `ai_service/orchestrators/chat_orchestrator.py`
- `ai_service/prompts/prompt_builder.py`
- `ai_service/tools/registry.py`
- `ai_service/tools/weight_analysis/`
- `ai_service/providers/backend/pet_weight_provider.py`

Java 后端关键文件：

- `backend/src/main/java/com/pet/api/ai/AiInternalPetController.java`
- `backend/src/main/java/com/pet/api/ai/dto/AiPetWeightRecordsResponse.java`
- `backend/src/main/java/com/pet/api/ai/dto/AiPetWeightRecordItemResponse.java`
- `backend/src/main/java/com/pet/service/PetWeightService.java`

## 环境变量

与本次改造相关的关键配置：

- `BASE_SYSTEM_PROMPT_FILE`
- `DECISION_PROMPT_FILE`
- `FINAL_RESPONSE_PROMPT_FILE`
- `TOOL_REGISTRY_PROMPT_FILE`
- `WEIGHT_ANALYSIS_TOOL_PROMPT_FILE`
- `TOOL_ENABLED_LIST`
- `WEIGHT_ANALYSIS_LIMIT`
- `BACKEND_BASE_URL`
- `BACKEND_TIMEOUT_SECONDS`

默认启用的 Tool：

- `weight_analysis`

## 日志

当前日志分三层：

1. AIService 应用级日志
   - `AIlog/application.log`

2. AIService 单次请求日志
   - `AIlog/YYYY-MM-DD/*_send.txt`
   - `AIlog/YYYY-MM-DD/*_decision_llm.txt`
   - `AIlog/YYYY-MM-DD/*_final_llm.txt`
   - `AIlog/YYYY-MM-DD/*_decision_llm_error.txt`
   - `AIlog/YYYY-MM-DD/*_final_llm_error.txt`
   - `AIlog/YYYY-MM-DD/*_weight_analysis_tool_llm.txt`
   - `AIlog/YYYY-MM-DD/*_weight_analysis_tool_llm_error.txt`

3. Java 后端与 AIService 交互日志
   - `backend/logs/backend/ai-interaction.log`

这样可以完整追踪：

- backend 发给 AIService 的请求
- AIService 第一轮决策输入输出
- Tool 内部与 LLM 的交互内容
- AIService 第二轮最终回答输入输出
