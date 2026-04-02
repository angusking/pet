# AIService

AIService 是独立的 Python AI 编排服务，对 Java 后端暴露统一的 HTTP 接口。

当前版本已经从单轮对话升级为“Question Rewrite 前置模块 + 两阶段对话 + Tool 路由”结构：

1. 先做 Question Rewrite，对用户问题进行语义标准化与结构化理解。
2. 第一轮再判断是否需要调用内部 Tool。
3. 如果不需要 Tool，直接返回结果。
4. 如果需要 Tool，先执行 Tool，再进入第二轮生成最终回答。

除此之外，AIService 现在还支持本地版本化 RAG 知识库：

1. 每个知识库版本独立存放 `jsonl` 和 FAISS 索引。
2. 通过 `active_kb.json` 控制当前生效版本。
3. 支持构建新版本、切换版本和 Retriever 热加载，无需重启服务。
4. 保留旧版本目录，便于回滚。

同时，AIService 现在会输出“正文 + 结构化字段”两部分信息：

- 正文：`answer`
- 结构化字段：`followUp`、`intent`、`riskLevel`、`checklist`、`services`、`followUps`、`followUpQuestions`、`actionCards`、`disclaimer`

Java 后端会把这些结构化字段单独保存并透传给前端，前端不再需要从 `message.content` 里反向解析 JSON。

## Question Rewrite 前置模块

Question Rewrite 位于 Tool Router 之前，只负责：

- 标准化用户问题
- 判断意图类型
- 判断是否属于 follow-up
- 给出是否建议 Tool、建议 Tool 名
- 判断是否可能需要后续知识检索
- 输出来源、置信度、标签和槽位

它不负责：

- 直接回答用户
- 直接执行 Tool

当前第一版采用“规则优先 + LLM 补充”的混合策略，重点覆盖：

- `weight_analysis`
- `weight_follow_up`
- `location_search`
- `general_knowledge`

## 当前已接入的 Tool

- `weight_analysis`
  - 根据宠物 ID 调 Java 后端内部接口读取最近体重记录
  - Tool 内部先整理原始记录
  - 再将整理后的上下文发送给 LLM 做趋势分析
  - 最后把分析结果注入第二轮 Prompt，生成最终用户可见回答
- `location_search`
  - 根据 Question Rewrite 和第一轮决策输出的地点槽位，提取地点描述与地点类型关键词
  - 直接调用高德 Web Service 文本搜索接口查询候选地点
  - 当前优先处理“浦东附近宠物医院”“北京朝阳宠物店”这类文本区域搜索
  - 如果缺少明确地点，只会返回缺少地点信息，不会盲目调用第三方接口

后续已预留的 Tool 扩展位：

- `service_lookup`
- `product_recommendation`

## 本地启动

```powershell
cd D:\pet\AIService
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
python run.py
```

说明：

- 当前 `requirements.txt` 已经包含 AIService 基础依赖和本地 RAG 依赖。
- 为了避免 Windows + Python 3.14 下的编译问题，当前项目已切换到 Python 3.12 环境。
- 如果重新创建虚拟环境，建议继续使用 Python 3.12。

## 主要接口

- `GET /health`
- `POST /api/ai/chat`
- `GET /kb/current`
- `GET /kb/versions`
- `POST /kb/rebuild`
- `POST /kb/switch`

## 关键目录

- `ai_service/orchestrators`
  - 聊天主编排流程
- `ai_service/capabilities`
  - Question Rewrite、决策、Tool 执行、RAG、安全等能力层
- `ai_service/tools`
  - 各类 Tool 的实现与注册表
- `ai_service/providers/backend`
  - Java 后端内部接口访问封装
- `ai_service/prompts/system`
  - 基础系统 Prompt、Question Rewrite Prompt、第一轮决策 Prompt、第二轮最终回答 Prompt
- `ai_service/prompts/tools`
  - Tool 注册表 Prompt 和 Tool 内部专用 Prompt
- `ai_service/rag`
  - 本地知识库版本管理、索引构建、Retriever 和相关 schema

## 本地 RAG 知识库

当前采用版本化目录结构：

```text
AIService/data/
├─ knowledge/
│  └─ {version}/
│     └─ rag_chunks.jsonl
├─ indexes/
│  └─ {version}/
│     ├─ faiss.index
│     ├─ metadata.json
│     └─ manifest.json
└─ active_kb.json
```

其中：

- `knowledge/{version}/rag_chunks.jsonl`
  - 某一版知识分块文件
- `indexes/{version}/faiss.index`
  - 该版本对应的向量索引
- `indexes/{version}/metadata.json`
  - 向量行号和原始 chunk 的映射
- `indexes/{version}/manifest.json`
  - 当前索引版本的概要信息
- `active_kb.json`
  - 当前激活版本，例如 `{ "active_version": "pet_v2" }`

当前 loader 已兼容图书切块风格的 `jsonl` 结构，例如：

```json
{
  "doc_id": "pet_book_001",
  "chunk_id": "pet_book_001_p0001_c0001",
  "content": "......",
  "source": "宠物疾病现代诊断与治疗操作技术实用手册",
  "page_start": 1,
  "page_end": 3,
  "part_title": "第一篇 ...",
  "chapter_title": "第一章 ...",
  "section_title": "第二节 ...",
  "tags": ["犬", "喂养"],
  "quality_score": 1.0
}
```

也就是说，当前不要求你手动把字段改成 `id/text`，系统会自动把：

- `chunk_id` 映射为内部 `id`
- `content` 映射为内部 `text`

推荐更新流程：

1. 放入新的 `rag_chunks.jsonl`
2. 调 `POST /kb/rebuild` 为该版本构建索引
3. 构建成功后调用 `POST /kb/switch`
4. Retriever 热加载新版本，在线请求立即生效

当前检索链路采用：

1. FAISS 向量召回
2. 本地轻量重排

也就是说，系统会先多召回一批候选 chunk，再结合问题和标题/章节/正文的字面匹配做一次轻量排序，
尽量把更贴近当前问题主题的片段排到前面。

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
- `QUESTION_REWRITE_PROMPT_FILE`
- `DECISION_PROMPT_FILE`
- `FINAL_RESPONSE_PROMPT_FILE`
- `TOOL_REGISTRY_PROMPT_FILE`
- `WEIGHT_ANALYSIS_TOOL_PROMPT_FILE`
- `AMAP_WEB_SERVICE_KEY`
- `AMAP_BASE_URL`
- `AMAP_SEARCH_PAGE_SIZE`
- `RAG_ENABLED`
- `RAG_DATA_DIR`
- `RAG_ACTIVE_FILE`
- `RAG_KNOWLEDGE_DIR`
- `RAG_INDEX_DIR`
- `RAG_TOP_K`
- `RAG_AUTO_LOAD_ON_START`
- `RAG_EMBEDDING_MODEL`
- `RAG_EMBEDDING_MODEL_PATH`
- `TOOL_ENABLED_LIST`
- `WEIGHT_ANALYSIS_LIMIT`

## 日志

默认会写三类日志：

1. 应用级日志
   - `AIlog/application.log`

2. 单次 AI 请求与 LLM 交互日志
   - `AIlog/YYYY-MM-DD/*_send.txt`
   - `AIlog/YYYY-MM-DD/*_question_rewrite_llm.txt`
   - `AIlog/YYYY-MM-DD/*_decision_llm.txt`
   - `AIlog/YYYY-MM-DD/*_final_llm.txt`
   - `AIlog/YYYY-MM-DD/*_weight_analysis_tool_llm.txt`
   - 对应的 `*_error.txt`

3. Java 后端与 AIService 的交互日志
   - `backend/logs/backend/ai-interaction.log`
