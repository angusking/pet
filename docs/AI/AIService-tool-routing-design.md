# AIService Tool 路由与两阶段对话设计

## 目标

本次改造把 AIService 从“单轮统一回答”升级为“Question Rewrite 前置层 + 第一轮决策 + Tool 执行 + 第二轮最终回答”的两阶段架构。

当前已落地 `weight_analysis` 和 `location_search`，但整体结构按多 Tool 扩展设计，后续可以继续平滑接入：

- `service_lookup`
- `product_recommendation`

## 总体流程

整体链路分为 5 步：

1. 用户问题进入 AIService。
2. Question Rewrite 前置模块先做语义标准化与结构化理解。
3. 第一轮对话只负责决策是否需要 Tool。
4. 如果需要 Tool，先执行 Tool。
5. Tool 结果被注入第二轮 Prompt，再生成最终返回给用户的结构化回答。

简化后的时序如下：

```text
用户问题
  -> Question Rewrite
  -> 第一轮决策
    -> 不需要 Tool：直接返回最终回答
    -> 需要 Tool：执行 Tool
      -> 将 Tool 结果注入第二轮 Prompt
      -> 生成最终回答
```

## Question Rewrite 前置层

Question Rewrite 位于 Tool Router 之前，只负责把用户问题“看明白”。

输出统一使用 `QuestionRewriteResult`，包含：

- `originalQuestion`
- `normalizedQuestion`
- `intentType`
- `suggestTool`
- `suggestedToolName`
- `followUp`
- `needKnowledgeRetrieval`
- `confidence`
- `source`
- `reasoningTags`
- `extractedSlots`

第一版采用“规则优先 + LLM 补充”的混合策略：

### 规则层优先覆盖

- 体重分析
- 体重分析追问
- 地点搜索
- 通用知识型问题

### LLM 层补充处理

用于规则不够确定的场景，例如：

- 口语表达很模糊
- 多轮追问边界不清晰
- 用户同时混入多个意图

### Rewrite 层职责边界

它只负责：

- 标准化问题
- 分类问题
- 标记是否 follow-up
- 给出 Tool 建议
- 给出知识检索建议

它不负责：

- 直接回答用户
- 直接执行 Tool

## 第一轮：Tool 决策

第一轮不直接做复杂分析，职责非常单一：

- 判断是否需要调用 Tool
- 选择最相关的一个 Tool
- 如果缺少必要参数，直接提示缺什么信息
- 如果无需 Tool，直接给出最终回答

第一轮输入不再只看原问题，而是优先参考 `rewriteResult`。

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
- Rewrite 结果
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
- `ai_service/prompts/system/question_rewrite_prompt.md`
  - Question Rewrite 专用提示词
- `ai_service/prompts/system/decision_prompt.md`
  - 第一轮 Tool 决策规则
- `ai_service/prompts/system/final_response_prompt.md`
  - 第二轮最终回答规则
- `ai_service/prompts/tools/tool_registry_prompt.md`
  - 可用 Tool 列表和通用选择规则
- `ai_service/prompts/tools/weight_analysis_llm_prompt.md`
  - 体重分析 Tool 内部给 LLM 的专用分析提示词

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

当前地点搜索 Tool 的结构如下：

```text
ai_service/tools/location_search/
├─ tool.py
├─ schemas.py
├─ provider.py
└─ __init__.py
```

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

第一轮决策 Prompt 使用的 Tool 列表，不只来自静态 Markdown，还会在运行时把注册表中当前启用的 Tool 信息拼进去。

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

## 地点搜索 Tool 的职责边界

### AIService Tool 职责

`location_search` Tool 当前负责：

1. 从显式输入、Question Rewrite 槽位和用户原问题中解析地点与关键词
2. 调用高德 Web Service 文本搜索接口
3. 把第三方返回统一归一成稳定结果结构，供第二轮回答 Prompt 使用
4. 如果缺少明确地点，则返回 `missing_location`，要求用户补充位置

### 当前范围与限制

- 当前只接入高德“文本搜索”能力
- 更适合“浦东附近宠物医院”“北京朝阳宠物店”这类文本区域查询
- 当前版本不依赖经纬度，因此还没有做周边搜索排序和距离计算

## 相关代码落点

AIService 关键文件：

- `ai_service/capabilities/question_rewrite_service.py`
- `ai_service/capabilities/question_rewrite_rules.py`
- `ai_service/capabilities/decision_service.py`
- `ai_service/capabilities/tool_service.py`
- `ai_service/orchestrators/chat_orchestrator.py`
- `ai_service/prompts/prompt_builder.py`
- `ai_service/prompts/system/question_rewrite_prompt.md`
- `ai_service/tools/registry.py`
- `ai_service/tools/weight_analysis/`
- `ai_service/tools/location_search/`
- `ai_service/providers/backend/pet_weight_provider.py`

Java 后端关键文件：

- `backend/src/main/java/com/pet/api/ai/AiInternalPetController.java`
- `backend/src/main/java/com/pet/api/ai/dto/AiPetWeightRecordsResponse.java`
- `backend/src/main/java/com/pet/api/ai/dto/AiPetWeightRecordItemResponse.java`
- `backend/src/main/java/com/pet/service/PetWeightService.java`

## 环境变量

与本次改造相关的关键配置：

- `BASE_SYSTEM_PROMPT_FILE`
- `QUESTION_REWRITE_PROMPT_FILE`
- `DECISION_PROMPT_FILE`
- `FINAL_RESPONSE_PROMPT_FILE`
- `TOOL_REGISTRY_PROMPT_FILE`
- `WEIGHT_ANALYSIS_TOOL_PROMPT_FILE`
- `AMAP_WEB_SERVICE_KEY`
- `AMAP_BASE_URL`
- `AMAP_SEARCH_PAGE_SIZE`
- `TOOL_ENABLED_LIST`
- `WEIGHT_ANALYSIS_LIMIT`
- `BACKEND_BASE_URL`
- `BACKEND_TIMEOUT_SECONDS`

## 日志

当前日志分三层：

1. AIService 应用级日志
   - `AIlog/application.log`

2. AIService 单次请求日志
   - `AIlog/YYYY-MM-DD/*_send.txt`
   - `AIlog/YYYY-MM-DD/*_question_rewrite_llm.txt`
   - `AIlog/YYYY-MM-DD/*_decision_llm.txt`
   - `AIlog/YYYY-MM-DD/*_final_llm.txt`
   - `AIlog/YYYY-MM-DD/*_weight_analysis_tool_llm.txt`
   - 对应的 `*_error.txt`

3. Java 后端与 AIService 交互日志
   - `backend/logs/backend/ai-interaction.log`
