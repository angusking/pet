# AI Service 功能及设计文档

## 1. 文档目的

本文档用于说明 AI Service 的功能定位、系统边界、核心流程与内部设计，作为后续开发、联调和迭代的基础参考。

文档目标如下：

- 明确 AI Service 在整体系统中的职责
- 说明 AI Service 提供的核心能力
- 给出推荐的模块划分与处理流程
- 约定与 Java 主后端之间的交互边界
- 为 V1、V2、V3 的迭代落地提供设计依据

## 2. 系统定位

AI Service 是独立的 Python 服务，负责所有与大模型相关的能力编排，不直接承担用户认证、宠物管理、业务主流程等通用后端职责。

它本质上是一个独立的 AI 编排服务（AI Orchestration Service），负责把聊天请求、宠物信息、上下文记忆、检索结果和工具结果整合起来，生成结构化 AI 输出。

核心职责包括：

- 接收 Java 服务传来的聊天请求
- 组织上下文与多轮对话记忆
- 调用大模型并管理 Prompt
- 执行问题重写、RAG、工具调用等 AI 能力
- 输出结构化结果并进行安全控制
- 记录 AI 调用日志，支持后续评估与优化

## 3. 整体架构

```text
前端 Vue
   ↓
Java Spring Boot 主后端
   - 用户登录 / JWT
   - 宠物资料管理
   - 聊天消息持久化
   - 业务数据查询
   - 调用 AI Service
   ↓
Python AI Service
   - Prompt 编排
   - 短期记忆
   - 问题重写
   - RAG 检索
   - Function Call / Tool 调用
   - 大模型调用
   - 输出解析与安全控制
   ↓
LLM / Redis / 向量数据库
```

## 4. 设计目标

AI Service 的设计目标如下：

- 统一管理与大模型相关的逻辑，避免逻辑分散在 Java 服务中
- 保证前端拿到稳定、可解析、可渲染的结构化结果
- 支持逐步扩展问题重写、RAG、工具调用和长期记忆等能力
- 将安全控制集中在 AI 服务内部处理，降低错误输出风险
- 降低主后端对具体模型和提示词实现的耦合度

## 5. 职责边界

### 5.1 Java 主后端职责

Java 服务负责：

- 用户认证
- 宠物信息管理
- 聊天消息 MySQL 持久化
- 业务数据查询
- 对前端提供统一接口
- 调用 AI Service
- 异常兜底与超时控制

### 5.2 AI Service 职责

Python AI Service 负责：

- Prompt 编排
- 记忆管理
- 问题重写
- RAG 检索
- Tool 调用
- 模型调用
- 输出解析
- 安全控制
- AI 调用日志记录

这样的边界可以保证业务系统与 AI 能力分层清晰，也便于后续独立扩展 AI 服务。

## 6. 核心功能设计

### 6.1 聊天编排

AI Service 的核心功能是接收一次聊天请求，并完成完整处理流程，包括上下文组装、模型调用和结果返回。

输入通常包括：

- 用户问题
- 宠物基础信息
- 最近对话
- 业务补充数据

输出通常包括：

- `answer`
- `riskLevel`
- `checklist`
- `services`
- `followUps`
- `disclaimer`

### 6.2 Prompt 管理

AI Service 统一维护提示词，不再分散写在 Java 服务里。

Prompt 管理建议包含：

- `system prompt`
- 输出格式约束
- 安全边界约束
- 工具调用提示
- RAG 引用提示

这样做的好处包括：

- 调整回答风格更集中
- 输出结构更容易统一
- 模型切换成本更低

### 6.3 短期记忆

AI Service 负责多轮上下文记忆，建议使用 Redis 存储最近几轮对话。

设计目标包括：

- 支持连续对话
- 避免每次从 MySQL 全量查询
- 控制 token 成本

建议默认只保留最近 6 到 10 轮消息。

### 6.4 问题重写

当用户提问过于模糊时，AI Service 可先执行一次问题重写，再进入检索和回答流程。

示例：

> 用户原问题：
> “它最近不太对劲”
>
> 重写后：
> “猫咪精神状态下降、食欲下降的可能原因”

问题重写的价值：

- 提高 RAG 检索准确率
- 提高工具调用判断准确率
- 降低大模型误判

### 6.5 RAG 检索

AI Service 可以接入宠物健康知识库，实现检索增强生成。

基本流程如下：

```text
用户问题
  ↓
问题重写
  ↓
向量检索
  ↓
返回相关知识片段
  ↓
拼接到 Prompt
  ↓
模型生成回答
```

适合扩展的知识类型包括：

- 宠物疫苗知识
- 饮食安全知识
- 常见症状与护理建议
- 日常养护知识

### 6.6 Function Call / Tool 调用

AI Service 可以根据问题自动决定是否调用工具，而不是让模型凭空回答。

首批可接入工具建议包括：

- `weight_analysis`：分析宠物体重趋势
- `food_safety_lookup`：判断食物是否适合宠物
- `pet_profile_lookup`：读取宠物基础信息
- `vaccine_reminder_check`：检查疫苗提醒

基本流程如下：

```text
用户问题
  ↓
模型判断是否需要工具
  ↓
调用工具
  ↓
拿到结果
  ↓
再生成最终回答
```

### 6.7 输出解析与校验

AI Service 负责把模型输出解析为前端可渲染的结构化 JSON，并做字段校验。

建议至少校验以下字段：

- `riskLevel` 是否合法
- `checklist` 是否为数组
- `services` 是否结构完整
- `disclaimer` 是否存在

这一层设计的主要目的，是提高前端渲染稳定性，避免模型输出格式波动导致页面异常。

### 6.8 安全控制

在宠物健康场景下，AI Service 需要加入基础安全边界。

例如：

- 不给药物剂量
- 不做明确诊断
- 高风险症状强制提示及时就医
- 避免危险医疗建议

这是 AI 应用工程中非常重要的一层控制。

### 6.9 AI 调用日志

AI Service 需要记录独立日志，用于后续优化与评估。

建议记录内容包括：

- `requestId / traceId`
- `conversationId`
- `model`
- `latency`
- `token usage`
- 是否用了 RAG
- 是否用了工具
- 是否做了问题重写
- 风险等级
- 失败原因

## 7. 接口设计

### 7.1 对外接口定位

AI Service 对 Java 主后端暴露 HTTP 接口，由 Java 主后端统一对前端提供接口。

推荐 V1 仅保留一个主入口：

- `POST /api/ai/chat`

### 7.2 请求示例

```json
{
  "requestId": "req-20260316-001",
  "conversationId": "conv-10001",
  "userId": 101,
  "pet": {
    "petId": 1,
    "name": "Mimi",
    "type": "cat",
    "age": 3,
    "weight": 4.2
  },
  "message": "它最近不太对劲",
  "recentMessages": [
    {
      "role": "user",
      "content": "这两天它食欲不太好"
    },
    {
      "role": "assistant",
      "content": "有没有伴随精神不佳或呕吐？"
    }
  ],
  "bizData": {
    "vaccines": [],
    "weightHistory": []
  }
}
```

### 7.3 响应示例

```json
{
  "requestId": "req-20260316-001",
  "answer": "猫咪最近精神或食欲异常，可能与消化不适、应激或感染等因素有关。若持续不吃、伴随呕吐或精神沉郁，建议尽快就医。",
  "riskLevel": "medium",
  "checklist": [
    "观察是否持续超过24小时",
    "确认是否有呕吐、腹泻或发热",
    "记录饮水和排便情况"
  ],
  "services": [],
  "followUps": [
    "最近有没有更换食物？",
    "是否伴随呕吐或腹泻？"
  ],
  "disclaimer": "本回答仅供宠物日常护理参考，不能替代执业兽医诊断。"
}
```

## 8. 数据结构设计

### 8.1 ChatRequest

建议字段：

- `requestId`
- `conversationId`
- `userId`
- `pet`
- `message`
- `recentMessages`
- `bizData`

### 8.2 ChatResponse

建议字段：

- `requestId`
- `answer`
- `riskLevel`
- `checklist`
- `services`
- `followUps`
- `disclaimer`

### 8.3 风险等级枚举

建议统一定义为：

- `low`
- `medium`
- `high`

## 9. 内部模块设计

推荐先使用以下简化目录：

```text
ai_service/
├── api/
│   └── chat.py
├── schemas/
│   ├── chat_request.py
│   └── chat_response.py
├── services/
│   ├── chat_orchestrator.py
│   ├── prompt_service.py
│   ├── memory_service.py
│   ├── rag_service.py
│   ├── tool_service.py
│   ├── safety_service.py
│   └── log_service.py
├── llm/
│   └── qwen_client.py
├── tools/
│   ├── base.py
│   └── weight_analysis.py
├── memory/
│   └── redis_memory.py
└── config/
    └── settings.py
```

各模块职责建议如下：

- `api/chat.py`：定义 HTTP 接口入口
- `schemas/`：定义请求响应模型与字段校验
- `chat_orchestrator.py`：串联完整 AI 处理流程
- `prompt_service.py`：管理系统提示词与模板拼装
- `memory_service.py`：统一处理短期记忆读写
- `rag_service.py`：执行知识检索和片段拼装
- `tool_service.py`：负责工具注册、选择与调用
- `safety_service.py`：执行安全规则校验与降级处理
- `log_service.py`：记录调用日志、指标与异常
- `qwen_client.py`：封装具体大模型调用逻辑

## 10. 核心流程设计

AI Service 的一次完整处理流程建议定义为：

1. 接收 Java 请求
2. 读取短期记忆
3. 判断是否需要问题重写
4. 判断是否需要 RAG
5. 判断是否需要工具调用
6. 构建 Prompt
7. 调用大模型
8. 解析结构化结果
9. 执行安全校验
10. 保存记忆与日志
11. 返回响应

对应时序可简化表示为：

```text
Java Backend -> AI Service: chat request
AI Service -> Redis: load recent memory
AI Service -> RAG / Tools: optional invoke
AI Service -> LLM: prompt request
LLM -> AI Service: structured response
AI Service -> Redis: save memory
AI Service -> Log Store: write log
AI Service -> Java Backend: final response
```

## 11. 非功能设计

### 11.1 可扩展性

- 模型调用层与业务编排层解耦
- RAG、Tool、Memory 能力可按阶段独立接入
- 支持后续扩展更多宠物领域工具与知识库

### 11.2 稳定性

- 统一返回结构化 JSON
- 对字段执行严格校验
- Java 主后端负责超时控制和异常兜底

### 11.3 安全性

- 输出前执行安全规则检查
- 高风险场景优先提示就医而不是继续推断
- 避免给出危险、诊断式或剂量级建议

### 11.4 可观测性

- 记录链路标识、模型信息、耗时和失败原因
- 区分是否触发问题重写、RAG、工具调用
- 为后续评估和优化留出数据基础

## 12. 分阶段实施建议

### 12.1 V1 必做

- FastAPI 接口
- Qwen 调用
- 结构化 JSON 输出
- Prompt 统一管理
- Redis 短期记忆
- Java 调 Python HTTP

### 12.2 V2 再做

- 体重分析工具调用
- 问题重写
- AI 调用评估日志

### 12.3 V3 再做

- RAG
- 向量数据库
- 长期记忆
- 更完整的 Tool Registry

## 13. 结论

AI Service 是一个独立的 Python AI 编排服务，主要负责管理上下文和记忆、统一大模型调用、处理问题重写与 RAG、支持工具调用、输出结构化结果，并承担 AI 安全控制与日志记录。

从工程设计上看，将 AI 能力集中到独立服务中，可以显著降低 Java 主后端的复杂度，也更适合后续逐步演进成完整的 AI 中台能力。
