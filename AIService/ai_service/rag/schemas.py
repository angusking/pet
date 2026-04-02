"""RAG 子模块使用的结构化模型。

当前知识文件 `rag_chunks.jsonl` 的实际字段并不是最初假设的极简结构，
而是更接近“图书切块结果”，包含：
- `doc_id`
- `chunk_id`
- `chunk_type`
- `content`
- `page_start/page_end`
- `part_title/chapter_title/section_title/subtopic_title`
- `quality_score`

因此这里做两层建模：
1. `RagChunk`：统一后的内部标准结构，供索引构建和检索使用；
2. `RagChunkMetadata`：索引行号和原始分块映射，供检索结果回显和 Prompt 组织使用。
"""

from __future__ import annotations

from datetime import datetime
from typing import Any

from pydantic import BaseModel, Field


class RagChunk(BaseModel):
    """单条知识分块。

    这里不再强制要求知识文件必须原生叫 `id/text`，
    而是允许加载器把实际字段映射进来，例如：
    - `chunk_id` -> `id`
    - `content` -> `text`
    """

    id: str = Field(..., description="分块唯一 ID。")
    text: str = Field(..., description="分块正文。")
    title: str = Field(default="", description="检索展示用标题。")
    source: str = Field(default="", description="来源说明。")
    category: str = Field(default="", description="分类标签。")
    tags: list[str] = Field(default_factory=list, description="可选标签。")
    metadata: dict[str, Any] = Field(default_factory=dict, description="扩展元数据。")


class RagChunkMetadata(BaseModel):
    """向量索引与原始分块的映射信息。

    除了最基础的标题、来源和正文外，这里把书籍切块相关的结构也保留下来，
    这样第二轮 Prompt 在引用 RAG 时，可以给模型更完整的上下文。
    """

    row_id: int = Field(..., description="在 FAISS 索引中的行号。")
    chunk_id: str = Field(..., description="原始分块 ID。")
    doc_id: str = Field(default="", description="原始文档 ID。")
    chunk_type: str = Field(default="", description="原始分块类型。")
    text: str = Field(..., description="分块正文。")
    title: str = Field(default="", description="整理后的展示标题。")
    source: str = Field(default="", description="来源。")
    category: str = Field(default="", description="分类。")
    tags: list[str] = Field(default_factory=list, description="标签。")
    page_start: int | None = Field(default=None, description="起始页码。")
    page_end: int | None = Field(default=None, description="结束页码。")
    part_title: str = Field(default="", description="篇章标题。")
    chapter_title: str = Field(default="", description="章节标题。")
    section_title: str = Field(default="", description="节标题。")
    subtopic_title: str = Field(default="", description="子主题标题。")
    quality_score: float | None = Field(default=None, description="切块质量分。")
    metadata: dict[str, Any] = Field(default_factory=dict, description="扩展元数据。")


class IndexManifest(BaseModel):
    """索引版本的概要信息。"""

    version: str = Field(..., description="知识库版本号。")
    source_file: str = Field(..., description="本次构建使用的知识文件路径。")
    doc_count: int = Field(..., description="本次进入索引的文档分块数量。")
    embedding_model: str = Field(..., description="使用的 embedding 模型。")
    vector_dimension: int = Field(..., description="向量维度。")
    created_at: datetime = Field(..., description="索引构建完成时间。")
    status: str = Field(default="ready", description="版本状态。")


class KnowledgeVersionInfo(BaseModel):
    """版本列表接口使用的聚合视图。"""

    version: str = Field(..., description="知识库版本号。")
    knowledge_exists: bool = Field(default=False, description="知识文件是否存在。")
    index_exists: bool = Field(default=False, description="索引文件是否完整。")
    status: str = Field(default="unknown", description="当前版本状态。")
    is_active: bool = Field(default=False, description="是否是当前激活版本。")
    manifest: IndexManifest | None = Field(default=None, description="索引概要信息。")


class BuildIndexRequest(BaseModel):
    """构建索引接口入参。"""

    version: str = Field(..., min_length=1, description="要构建的版本号。")
    knowledge_file_path: str | None = Field(
        default=None,
        description="可选的知识文件路径；为空时使用默认目录 data/knowledge/{version}/rag_chunks.jsonl。",
    )


class BuildIndexResponse(BaseModel):
    """构建索引接口返回。"""

    version: str = Field(..., description="构建的版本号。")
    status: str = Field(..., description="构建状态。")
    manifest: IndexManifest | None = Field(default=None, description="构建生成的索引概要。")
    message: str = Field(default="", description="结果说明。")


class SwitchVersionRequest(BaseModel):
    """切换激活版本接口入参。"""

    version: str = Field(..., min_length=1, description="要切换到的版本号。")


class CurrentVersionResponse(BaseModel):
    """当前激活版本接口返回。"""

    active_version: str | None = Field(default=None, description="active_kb.json 中记录的当前激活版本。")
    loaded_version: str | None = Field(default=None, description="Retriever 当前已加载的版本。")
    status: str = Field(default="empty", description="当前状态，例如 ready / empty / broken。")


class RetrievedChunk(BaseModel):
    """单条检索结果。"""

    score: float = Field(..., description="向量相似度分数。")
    chunk_id: str = Field(..., description="分块 ID。")
    text: str = Field(..., description="分块正文。")
    title: str = Field(default="", description="标题。")
    source: str = Field(default="", description="来源。")
    category: str = Field(default="", description="分类。")
    tags: list[str] = Field(default_factory=list, description="标签。")
    page_start: int | None = Field(default=None, description="起始页码。")
    page_end: int | None = Field(default=None, description="结束页码。")
    part_title: str = Field(default="", description="篇章标题。")
    chapter_title: str = Field(default="", description="章节标题。")
    section_title: str = Field(default="", description="节标题。")
    subtopic_title: str = Field(default="", description="子主题标题。")
    quality_score: float | None = Field(default=None, description="切块质量分。")
    metadata: dict[str, Any] = Field(default_factory=dict, description="扩展元数据。")

