"""本地 FAISS Retriever。"""

from __future__ import annotations

import json
from threading import RLock

from ai_service.core.logging import get_logger
from ai_service.rag.embedding_provider import LocalEmbeddingProvider
from ai_service.rag.exceptions import KnowledgeVersionInvalidError, RetrieverNotReadyError
from ai_service.rag.knowledge_manager import KnowledgeManager
from ai_service.rag.schemas import IndexManifest, RagChunkMetadata, RetrievedChunk

logger = get_logger(__name__)

try:
    import faiss
    import numpy as np
except Exception:  # pragma: no cover
    faiss = None  # type: ignore[assignment]
    np = None  # type: ignore[assignment]


class FaissRetriever:
    """加载当前激活版本索引并执行查询。

    这里使用锁来保护“索引对象 + metadata + 当前版本”这组三元状态，
    确保热加载时不会出现半切换状态。
    """

    def __init__(
        self,
        knowledge_manager: KnowledgeManager,
        embedding_provider: LocalEmbeddingProvider,
    ) -> None:
        self._knowledge_manager = knowledge_manager
        self._embedding_provider = embedding_provider
        self._lock = RLock()
        self._index = None
        self._metadata: list[RagChunkMetadata] = []
        self._manifest: IndexManifest | None = None
        self._current_version: str | None = None

    def load_active(self) -> str | None:
        """启动时加载当前激活版本。

        这一步通常在应用启动阶段调用。
        如果 active_kb.json 还不存在，不会报错，而是让服务以“RAG 未就绪”的状态启动。
        这样聊天主链路仍然能工作，只是不会使用知识检索。
        """

        active_version = self._knowledge_manager.get_active_version()
        if not active_version:
            logger.info("no active rag version found on startup")
            return None
        self.reload(active_version)
        return active_version

    def reload(self, version: str) -> None:
        """热加载指定版本索引。

        加载顺序刻意分成“先读完整新对象，再原子替换”，
        避免在线请求看到索引和 metadata 不一致的中间状态。
        """

        if faiss is None:
            raise RetrieverNotReadyError("未安装 faiss-cpu，无法加载本地索引。")

        self._knowledge_manager.ensure_ready_version(version)
        new_index = faiss.read_index(str(self._knowledge_manager.get_index_file(version)))
        metadata_payload = json.loads(
            self._knowledge_manager.get_metadata_file(version).read_text(encoding="utf-8")
        )
        manifest_payload = json.loads(
            self._knowledge_manager.get_manifest_file(version).read_text(encoding="utf-8")
        )
        new_metadata = [RagChunkMetadata.model_validate(item) for item in metadata_payload]
        new_manifest = IndexManifest.model_validate(manifest_payload)

        with self._lock:
            self._index = new_index
            self._metadata = new_metadata
            self._manifest = new_manifest
            self._current_version = version
        logger.info("reload rag retriever success, version=%s, docCount=%s", version, len(new_metadata))

    def current_version(self) -> str | None:
        """返回检索器当前已加载版本。"""

        with self._lock:
            return self._current_version

    def search(self, query: str, top_k: int) -> list[RetrievedChunk]:
        """执行向量检索。

        当前流程是：
        1. 把查询做 embedding；
        2. 在当前已加载的 FAISS 索引里做 top_k 召回；
        3. 根据 row_id 反查 metadata；
        4. 组装成上层可直接消费的 RetrievedChunk。

        注意这里还没有做重排。
        重排放在 RagService 里完成，原因是：
        - Retriever 只负责“把候选召回出来”；
        - RagService 负责“把候选整理成更适合 Prompt 的最终上下文”。
        """

        if not query.strip():
            return []
        if np is None:
            raise RetrieverNotReadyError("未安装 numpy，无法执行向量检索。")

        with self._lock:
            if self._index is None or self._current_version is None:
                raise RetrieverNotReadyError("Retriever 尚未加载任何激活版本。")
            index = self._index
            metadata = list(self._metadata)
            # 这里复制 metadata 引用列表，而不是持锁执行整个查询。
            # 目的是尽量缩短锁持有时间，避免热加载时和在线查询互相阻塞太久。

        query_vector = self._embedding_provider.embed_query(query)
        query_array = np.asarray([query_vector], dtype="float32")
        faiss.normalize_L2(query_array)
        scores, row_ids = index.search(query_array, top_k)

        results: list[RetrievedChunk] = []
        for row_id, score in zip(row_ids[0], scores[0]):
            if row_id < 0:
                continue
            if row_id >= len(metadata):
                raise KnowledgeVersionInvalidError(
                    f"索引与 metadata 不一致，rowId={row_id}, metadataCount={len(metadata)}"
                )
            item = metadata[row_id]
            results.append(
                RetrievedChunk(
                    score=float(score),
                    chunk_id=item.chunk_id,
                    text=item.text,
                    title=item.title,
                    source=item.source,
                    category=item.category,
                    tags=item.tags,
                    page_start=item.page_start,
                    page_end=item.page_end,
                    part_title=item.part_title,
                    chapter_title=item.chapter_title,
                    section_title=item.section_title,
                    subtopic_title=item.subtopic_title,
                    quality_score=item.quality_score,
                    metadata=item.metadata,
                )
            )
        return results
