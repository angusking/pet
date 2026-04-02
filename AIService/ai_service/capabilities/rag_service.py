"""RAG 检索能力。

这一层不直接处理版本目录、索引文件和 FAISS 细节，
而是把它们收敛成编排层真正关心的两个动作：
1. `retrieve(query)`：拿知识上下文
2. `reload(version)`：切换激活版本后热加载

这样 ChatOrchestrator 只知道“RAG 能不能给我上下文”，
不需要知道背后是本地文件、FAISS 还是其他向量引擎。
"""

from __future__ import annotations

from ai_service.core.logging import get_logger
from ai_service.rag.exceptions import RetrieverNotReadyError
from ai_service.rag.reranker import LightweightReranker
from ai_service.rag.retriever import FaissRetriever

logger = get_logger(__name__)


class RagService:
    """检索增强服务。

    这是编排层真正接触到的 RAG 入口。
    上层不需要知道：
    - 当前使用的是哪一版知识库；
    - 索引文件如何组织；
    - FAISS 如何检索；
    - 重排规则如何打分。

    上层只需要知道两件事：
    1. 给我一个问题，我尽量返回适合写进 Prompt 的知识上下文；
    2. 当知识版本切换时，我能重新加载。
    """

    _MAX_CHUNK_TEXT_LENGTH = 500
    _FETCH_MULTIPLIER = 3

    def __init__(self, retriever: FaissRetriever, top_k: int, enabled: bool) -> None:
        self._retriever = retriever
        self._top_k = top_k
        self._enabled = enabled
        self._reranker = LightweightReranker()

    async def retrieve(self, query: str) -> str | None:
        """根据问题检索知识片段。

        这里返回一段稳定的文本，而不是把原始检索结构直接塞给 Prompt。
        原因是编排层和 Prompt 更适合消费“整理过的上下文”，
        而不是再自行理解多层嵌套对象。
        """

        # 如果环境配置里关闭了 RAG，这一层直接返回 None，
        # 让聊天链路退化成“无知识检索”的正常模式。
        if not self._enabled:
            return None
        try:
            # 先多召回一批，再用本地轻量重排压缩成最终 top_k。
            # 这样可以保留向量召回的语义覆盖，同时减少“主题相近但答非所问”的片段排在最前面。
            fetched_chunks = self._retriever.search(
                query=query,
                top_k=max(self._top_k, self._top_k * self._FETCH_MULTIPLIER),
            )
        except RetrieverNotReadyError as exc:
            logger.info("rag retriever not ready, skip retrieval, error=%s", exc)
            return None

        if not fetched_chunks:
            return None

        # Retriever 返回的是原始候选；这里再做一次轻量重排，
        # 让最终进入 Prompt 的上下文尽量围绕当前问题主题展开。
        chunks = self._reranker.rerank(query=query, chunks=fetched_chunks, top_k=self._top_k)

        lines: list[str] = []
        for index, chunk in enumerate(chunks, start=1):
            # 这里不直接把原始 JSON 结构塞给 Prompt，
            # 而是转成对 LLM 更友好的“半结构化文本块”。
            # 这样既保留来源、页码、质量分等信息，也避免 Prompt 里嵌套过深。
            lines.append(f"[{index}] score={chunk.score:.4f}")
            if chunk.title:
                lines.append(f"标题：{chunk.title}")
            if chunk.source:
                lines.append(f"来源：{chunk.source}")
            if chunk.category:
                lines.append(f"分类：{chunk.category}")
            if chunk.page_start is not None and chunk.page_end is not None:
                lines.append(f"页码：{chunk.page_start}-{chunk.page_end}")
            elif chunk.page_start is not None:
                lines.append(f"页码：{chunk.page_start}")
            if chunk.chapter_title and chunk.chapter_title != chunk.title:
                lines.append(f"章节：{chunk.chapter_title}")
            if chunk.section_title:
                lines.append(f"小节：{chunk.section_title}")
            if chunk.quality_score is not None:
                lines.append(f"质量分：{chunk.quality_score:.2f}")
            lines.append(f"内容：{self._truncate_text(chunk.text)}")
            lines.append("")
        return "\n".join(lines).strip()

    def reload(self, version: str) -> None:
        """热加载指定版本。"""

        if not self._enabled:
            raise RetrieverNotReadyError("RAG 当前已禁用，无法热加载知识库版本。")
        self._retriever.reload(version)

    def current_version(self) -> str | None:
        """返回当前已加载版本。"""

        return self._retriever.current_version()

    def _truncate_text(self, text: str) -> str:
        """控制单个 chunk 写入 Prompt 的长度。

        如果不做截断，top_k 个长 chunk 很容易把 Prompt 上下文撑爆，
        反而让模型抓不住重点。这里先做保守裁剪，优先保证“多条候选都能进去”。
        """

        normalized = text.strip()
        if len(normalized) <= self._MAX_CHUNK_TEXT_LENGTH:
            return normalized
        return normalized[: self._MAX_CHUNK_TEXT_LENGTH] + "..."
