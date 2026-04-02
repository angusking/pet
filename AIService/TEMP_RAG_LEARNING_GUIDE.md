# AIService RAG 学习笔记

这是一份专门面向 `AIService` 本地 RAG 子系统的学习文档。

它的目标不是讲通用 RAG 理论，而是帮助你快速理解这几个问题：

1. 这个项目里的本地 RAG 是怎么组织的
2. 一份 `rag_chunks.jsonl` 是怎样变成在线可检索知识库的
3. 版本管理、索引构建、热加载、检索和重排分别在哪里做
4. 以后要继续优化，应该从哪些位置下手

---

## 1. 先用一句话理解这套 RAG

当前 `AIService` 里的 RAG 是一套“本地文件 + 本地 embedding + FAISS + 版本切换 + 在线热加载”的知识检索系统。

它不是把知识直接写死在 Prompt 里，而是：

1. 先把知识切成 `jsonl`
2. 为某个版本构建独立索引
3. 通过 `active_version` 决定当前生效版本
4. 在线请求按需检索相关知识片段
5. 再把检索结果注入到最终 Prompt 中

---

## 2. 目录结构怎么看

当前和 RAG 直接相关的目录主要是这几块：

```text
AIService/
├── ai_service/
│   ├── rag/
│   │   ├── embedding_provider.py
│   │   ├── exceptions.py
│   │   ├── index_builder.py
│   │   ├── jsonl_loader.py
│   │   ├── knowledge_manager.py
│   │   ├── reranker.py
│   │   ├── retriever.py
│   │   └── schemas.py
│   ├── capabilities/
│   │   └── rag_service.py
│   └── api/
│       └── kb_admin.py
└── data/
    ├── knowledge/
    │   └── {version}/rag_chunks.jsonl
    ├── indexes/
    │   └── {version}/
    │       ├── faiss.index
    │       ├── metadata.json
    │       └── manifest.json
    └── active_kb.json
```

你可以把它理解成四层：

- 数据层：`data/knowledge`、`data/indexes`、`active_kb.json`
- 构建层：`jsonl_loader.py`、`index_builder.py`、`embedding_provider.py`
- 检索层：`retriever.py`、`reranker.py`、`rag_service.py`
- 管理层：`knowledge_manager.py`、`kb_admin.py`

---

## 3. 数据文件各自是什么

### 3.1 `rag_chunks.jsonl`

这是知识源文件。

当前项目里每一行是一个 JSON，对应一个知识分块。  
它不是最初假设的极简 `id/text` 结构，而更接近图书切块结果，常见字段包括：

- `doc_id`
- `chunk_id`
- `chunk_type`
- `content`
- `page_start`
- `page_end`
- `part_title`
- `chapter_title`
- `section_title`
- `subtopic_title`
- `quality_score`

项目不会要求你先手动把它改成标准格式，而是由 `JsonlKnowledgeLoader` 自动转换。

---

### 3.2 `faiss.index`

这是向量索引本体。

它只负责存向量和做相似度检索，不保存原始正文、标题和页码。

所以它必须和 `metadata.json` 配合使用。

---

### 3.3 `metadata.json`

这是 “FAISS 行号 -> 原始 chunk 信息” 的映射文件。

为什么需要它？

因为 FAISS 检索出来的通常是：

- 第几行命中
- 该行分数是多少

但模型真正需要的是：

- 这段内容的标题
- 这段来自哪里
- 页码是什么
- 正文内容是什么

这些都靠 `metadata.json` 补回来。

---

### 3.4 `manifest.json`

这是一个版本摘要文件，方便排查和管理。

里面通常记录：

- 版本号
- 构建时使用的知识文件
- 进入索引的 chunk 数
- embedding 模型
- 向量维度
- 构建时间

它不是检索必需文件，但对管理非常有用。

---

### 3.5 `active_kb.json`

这是当前激活版本记录文件。

例如：

```json
{
  "active_version": "v0402"
}
```

它表示：

- 磁盘层面当前希望生效的是哪个版本

服务启动或切换时，`Retriever` 会根据它加载对应索引。

---

## 4. 整体执行流程

RAG 的完整流转可以拆成两条链路。

### 4.1 构建链路

```text
rag_chunks.jsonl
  -> JsonlKnowledgeLoader
  -> IndexBuilder
  -> LocalEmbeddingProvider
  -> faiss.index + metadata.json + manifest.json
```

### 4.2 在线检索链路

```text
用户问题
  -> Question Rewrite 判断 needKnowledgeRetrieval
  -> RagService.retrieve(query)
  -> FaissRetriever.search()
  -> LightweightReranker.rerank()
  -> 组织成 ragContext 文本
  -> 注入最终 Prompt
```

---

## 5. 各个文件分别做什么

### 5.1 `schemas.py`

[schemas.py](/D:/pet/AIService/ai_service/rag/schemas.py)

这一层定义 RAG 子系统的数据结构。

最重要的几个模型是：

- `RagChunk`
  - 内部统一后的知识分块
- `RagChunkMetadata`
  - 索引行号和原始 chunk 的映射
- `RetrievedChunk`
  - 一次检索返回给上层的结果
- `IndexManifest`
  - 当前版本索引概要

可以把它理解成：

- loader、builder、retriever 都通过这些模型说话
- 这样字段不会在多个文件里到处飘

---

### 5.2 `jsonl_loader.py`

[jsonl_loader.py](/D:/pet/AIService/ai_service/rag/jsonl_loader.py)

这一层负责把知识文件读进来，并映射成统一结构。

它当前做了几件很关键的事：

- 兼容 `chunk_id -> id`
- 兼容 `content -> text`
- 清理标题里的 OCR 噪声
- 合成更适合展示和检索的标题
- 把额外字段收进 `metadata`

这里的设计很重要，因为它把“知识源格式”跟“内部索引格式”隔离开了。

换句话说：

- 即使以后知识生产链路改字段
- 也尽量只需要动 loader，而不是整个 RAG 系统都跟着改

---

### 5.3 `embedding_provider.py`

[embedding_provider.py](/D:/pet/AIService/ai_service/rag/embedding_provider.py)

这一层负责本地 embedding。

当前采用：

- `sentence-transformers`
- 本地或 Hugging Face 模型

它用了懒加载：

- 服务启动时不立刻加载模型
- 真正构建索引或检索时才加载

这样做的好处：

- 启动更快
- 不启用 RAG 时不会额外占用内存

---

### 5.4 `index_builder.py`

[index_builder.py](/D:/pet/AIService/ai_service/rag/index_builder.py)

这是索引构建器。

它负责：

1. 找到某个版本的知识文件
2. 调 loader 读取 chunk
3. 过滤明显噪声 chunk
4. 生成 embedding
5. 构建 FAISS 索引
6. 落盘索引和 metadata

这里有两个设计点值得特别注意。

#### 第一，不自动切换版本

它只负责“构建”，不负责“激活”。

这样即使新索引构建好了，也不会立刻影响线上检索。

真正切换生效要走单独的 `/kb/switch`。

#### 第二，会做轻量过滤

目前会过滤：

- 过短内容
- 质量分过低
- 目录页样式噪声
- 符号异常过多的噪声段

这层过滤不是为了做复杂清洗，而是为了先把最伤召回质量的片段拦掉。

---

### 5.5 `knowledge_manager.py`

[knowledge_manager.py](/D:/pet/AIService/ai_service/rag/knowledge_manager.py)

这一层只负责版本管理。

它负责：

- 知道知识目录在哪
- 知道索引目录在哪
- 知道 `active_kb.json` 在哪
- 能列出所有版本
- 能判断某个版本是不是 ready
- 能读写 active_version

它不做检索，也不做 embedding。

这一层的价值在于把“版本状态”单独收口，避免不同模块各自去猜目录结构。

---

### 5.6 `retriever.py`

[retriever.py](/D:/pet/AIService/ai_service/rag/retriever.py)

这是在线检索器。

它负责：

- 启动时加载当前激活版本
- 热加载指定版本
- 对用户 query 做向量检索
- 根据 row_id 回查 metadata

这里有一个关键实现细节：使用锁保护内部状态。

内部有三组强关联对象：

- 当前 FAISS 索引对象
- 当前 metadata
- 当前已加载版本号

热加载时如果不加保护，可能出现：

- 索引已经切到新版本
- metadata 还是旧版本

那检索结果就会错位。  
所以这里会先把新对象完整读出来，再一次性替换。

---

### 5.7 `reranker.py`

[reranker.py](/D:/pet/AIService/ai_service/rag/reranker.py)

这是轻量重排器。

当前系统不是直接把 FAISS 返回结果原样塞进 Prompt，而是先多召回一批，再做一次本地重排。

原因是：

- 向量召回能保证“语义相近”
- 但未必保证“主题最聚焦”

例如问：

`狗为什么不能长期只吃肉？`

向量召回可能会同时命中：

- 喂养章节
- 肝病章节
- 营养缺乏章节

它们都和“长期、吃、肉、健康”有关系，但不是都同样适合直接回答问题。

轻量重排器当前会综合：

- 原始向量分
- 问题短语和标题的命中
- 问题短语和正文的命中
- 领域提示词
- 喂养问题的章节偏置

它不是昂贵的 reranker 模型，但足够先把明显更合适的片段往前推。

---

### 5.8 `rag_service.py`

[rag_service.py](/D:/pet/AIService/ai_service/capabilities/rag_service.py)

这是编排层真正依赖的入口。

它负责：

- 向 Retriever 要候选
- 调用轻量重排
- 把结果组织成适合 Prompt 的文本
- 对单段文本做截断

这里很重要的一点是：

它返回的不是原始对象数组，而是一段半结构化文本。

例如：

```text
[1] score=0.8123
标题：...
来源：...
页码：...
内容：...
```

原因是 LLM 最擅长消费的仍然是文本上下文，而不是深层嵌套 JSON。

---

### 5.9 `kb_admin.py`

[kb_admin.py](/D:/pet/AIService/ai_service/api/kb_admin.py)

这一层暴露知识库管理接口。

主要接口有：

- `GET /kb/current`
- `GET /kb/versions`
- `POST /kb/rebuild`
- `POST /kb/switch`

这几个接口把原本只能靠手工文件操作完成的事情，变成了明确的服务能力。

尤其是：

- `/kb/rebuild` 只构建，不切换
- `/kb/switch` 才真正切换并热加载

这就是当前版本管理的核心约束。

---

## 6. 为什么要做版本化管理

这里的设计不是“改一份知识文件，覆盖掉旧索引”，而是“每个版本独立保存”。

这样做的好处有 4 个：

### 6.1 可回滚

如果 `v0403` 构建成功但召回质量变差，可以立即切回 `v0402`。

### 6.2 构建和上线解耦

新版本可以先构建好、检查好，再决定什么时候切换。

### 6.3 避免覆盖污染

不会因为一次错误构建把原来的索引直接弄坏。

### 6.4 便于排查

你能知道：

- 哪个版本在运行
- 什么时候构建的
- 用了什么 embedding 模型

---

## 7. 当前聊天链路里 RAG 什么时候会被用到

现在不是所有问题都默认走 RAG。

链路大致是：

1. `Question Rewrite` 先判断问题意图
2. 如果判断为通用知识型问题，并且 `needKnowledgeRetrieval=true`
3. 才会调用 `RagService.retrieve()`

例如这类问题更容易触发：

- `狗为什么不能长期只吃肉？`
- `成年狗每天饮水量一般是多少？`
- `猫为什么突然掉毛？`

而像这些通常不靠 RAG：

- 体重分析
- 体重追问
- 地点搜索

---

## 8. RAG 结果为什么不是直接回答，而是作为 Prompt 上下文

这一点很关键。

RAG 当前的职责不是“自己生成最终答案”，而是：

- 找到相关知识
- 整理成高质量上下文
- 提供给最终回答阶段的大模型

也就是说：

- `Retriever` 负责找
- `RagService` 负责整理
- 最终回答还是由主对话 LLM 生成

这样链路更统一，也便于把 RAG 和 Tool、对话上下文一起融合。

---

## 9. 当前已经做了哪些针对实际知识文件的适配

这套 RAG 不再要求知识源必须是理想化格式，而是已经对当前项目实际的 `rag_chunks.jsonl` 做了适配。

目前主要适配了：

- `chunk_id -> id`
- `content -> text`
- 章节标题合成
- OCR 标题清理
- 页码保留
- `quality_score` 保留
- 索引前低质量过滤
- 结果回显时展示章节、页码、质量分
- 向量召回后再做轻量重排

所以它已经不是一个纯占位 RAG，而是项目级可运行实现。

---

## 10. 如果你要排查问题，先看哪里

### 10.1 知识文件有没有被正确识别

看：

- [jsonl_loader.py](/D:/pet/AIService/ai_service/rag/jsonl_loader.py)

### 10.2 索引有没有构建成功

看：

- [index_builder.py](/D:/pet/AIService/ai_service/rag/index_builder.py)
- `/kb/rebuild` 返回
- `data/indexes/{version}/`

### 10.3 当前激活和已加载版本是不是一致

看：

- `/kb/current`
- [active_kb.json](/D:/pet/AIService/data/active_kb.json)

### 10.4 为什么某个问题没走 RAG

看：

- Question Rewrite 日志
- send 日志里的 `rewriteNeedKnowledgeRetrieval`
- send 日志里的 `usedRag`

### 10.5 为什么召回结果不准

先看：

- `retriever.py` 是否召回了相关候选
- `reranker.py` 是否把相关候选排到了前面
- `rag_service.py` 是否截断过度

---

## 11. 你后续可以继续优化哪些点

如果后面要继续提升效果，优先级建议是：

### 11.1 更强的标题清洗

当前仍有少量 OCR 噪声标题残留，可以继续加强目录页和标题清洗规则。

### 11.2 更稳的质量过滤

现在只做轻量过滤，后续可以针对目录页、噪声页、极短页做更细规则。

### 11.3 更强的重排

当前是本地轻量重排。  
后续如果效果要求更高，可以替换成更专业的 reranker 模型。

### 11.4 查询改写与 RAG 协同

现在 Question Rewrite 已经能判断是否需要知识检索，后续还可以针对知识问题生成更适合检索的 rewrite query。

### 11.5 Prompt 注入策略

目前 `ragContext` 是稳定文本拼装，后续可以继续优化格式和长度预算。

---

## 12. 推荐阅读顺序

如果你第一次看这套 RAG，建议按这个顺序：

1. [schemas.py](/D:/pet/AIService/ai_service/rag/schemas.py)
2. [jsonl_loader.py](/D:/pet/AIService/ai_service/rag/jsonl_loader.py)
3. [index_builder.py](/D:/pet/AIService/ai_service/rag/index_builder.py)
4. [knowledge_manager.py](/D:/pet/AIService/ai_service/rag/knowledge_manager.py)
5. [retriever.py](/D:/pet/AIService/ai_service/rag/retriever.py)
6. [reranker.py](/D:/pet/AIService/ai_service/rag/reranker.py)
7. [rag_service.py](/D:/pet/AIService/ai_service/capabilities/rag_service.py)
8. [kb_admin.py](/D:/pet/AIService/ai_service/api/kb_admin.py)

按这个顺序读，会比较容易把“构建”和“在线检索”两条链路串起来。

---

## 13. 一句总结

当前 `AIService` 里的 RAG 不是一个抽象概念，而是一套已经分层落地的本地知识检索系统：

- `loader` 负责把知识文件标准化
- `builder` 负责把知识做成版本化索引
- `manager` 负责管理 active_version
- `retriever` 负责在线向量召回
- `reranker` 负责把结果排得更贴近问题
- `rag_service` 负责给大模型提供最终知识上下文

如果你掌握了这几个文件的职责分工，就已经抓住了这套 RAG 的主干。
