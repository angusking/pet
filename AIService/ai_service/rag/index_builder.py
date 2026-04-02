"""版本化索引构建器。"""

from __future__ import annotations

import json
import re
from datetime import datetime
from pathlib import Path

from ai_service.core.logging import get_logger
from ai_service.core.settings import BASE_DIR
from ai_service.rag.embedding_provider import LocalEmbeddingProvider
from ai_service.rag.exceptions import IndexBuildError
from ai_service.rag.jsonl_loader import JsonlKnowledgeLoader
from ai_service.rag.knowledge_manager import KnowledgeManager
from ai_service.rag.schemas import BuildIndexResponse, IndexManifest, RagChunk, RagChunkMetadata

logger = get_logger(__name__)

try:
    import faiss
    import numpy as np
except Exception:  # pragma: no cover
    faiss = None  # type: ignore[assignment]
    np = None  # type: ignore[assignment]


class IndexBuilder:
    """读取指定版本 JSONL 并生成独立 FAISS 索引。

    除了基础构建流程外，这里增加一层“进入索引前的轻量过滤”，
    目标不是做复杂清洗，而是先剔除最明显的低质量噪声：
    - 过短内容
    - 明显目录页/排版噪声
    - 质量分过低
    """

    _MIN_TEXT_LENGTH = 80
    _MIN_QUALITY_SCORE = 0.7

    def __init__(
        self,
        knowledge_manager: KnowledgeManager,
        embedding_provider: LocalEmbeddingProvider,
    ) -> None:
        self._knowledge_manager = knowledge_manager
        self._embedding_provider = embedding_provider
        self._loader = JsonlKnowledgeLoader()

    def build(self, version: str, knowledge_file_path: str | None = None) -> BuildIndexResponse:
        """为指定版本构建索引。

        整体流程是：
        1. 找到知识文件；
        2. 读取并标准化 JSONL；
        3. 过滤明显不适合进入索引的噪声 chunk；
        4. 生成 embedding；
        5. 构建并落盘独立版本的 FAISS 索引；
        6. 生成 metadata.json 和 manifest.json。

        注意这里只负责“产出新版本索引”，不负责切换 active_version。
        这样可以保证：只有构建成功的版本，才有资格被后续 `/kb/switch` 激活。
        """

        if faiss is None or np is None:
            raise IndexBuildError("未安装 faiss-cpu 或 numpy，无法构建本地向量索引。")

        knowledge_file = (
            self._resolve_path(knowledge_file_path)
            if knowledge_file_path
            else self._knowledge_manager.get_knowledge_file(version)
        )
        index_dir = self._knowledge_manager.get_index_dir(version)
        if index_dir.exists() and any(index_dir.iterdir()):
            raise IndexBuildError(f"目标版本索引目录已存在内容，拒绝覆盖: {index_dir}")

        logger.info("start building rag index, version=%s, knowledgeFile=%s", version, knowledge_file)
        raw_chunks = self._loader.load(knowledge_file)
        chunks = [chunk for chunk in raw_chunks if self._should_index(chunk)]
        if not chunks:
            raise IndexBuildError(f"过滤后没有可用 chunk，无法构建索引: version={version}")

        # embedding 文本不直接等于正文。
        # 这里把篇章/章节标题也拼进去，目的是让向量空间同时吸收“正文语义”和“结构主题”。
        # 对图书型知识库来说，这通常能明显改善“按章节主题召回”的效果。
        texts = [self._build_embedding_text(chunk) for chunk in chunks]
        embeddings = self._embedding_provider.embed_documents(texts)
        if not embeddings:
            raise IndexBuildError(f"没有生成任何向量，无法构建索引: version={version}")

        vectors = np.asarray(embeddings, dtype="float32")
        # 使用内积检索时，先做 L2 归一化，可以把分数近似理解为 cosine 相似度。
        faiss.normalize_L2(vectors)
        vector_dimension = int(vectors.shape[1])
        index = faiss.IndexFlatIP(vector_dimension)
        index.add(vectors)

        metadata_items = [self._build_metadata(row_id, chunk) for row_id, chunk in enumerate(chunks)]
        manifest = IndexManifest(
            version=version,
            source_file=str(knowledge_file),
            doc_count=len(chunks),
            embedding_model=self._embedding_provider.model_name(),
            vector_dimension=vector_dimension,
            created_at=datetime.now(),
            status="ready",
        )

        index_dir.mkdir(parents=True, exist_ok=True)
        faiss.write_index(index, str(self._knowledge_manager.get_index_file(version)))
        self._knowledge_manager.get_metadata_file(version).write_text(
            json.dumps([item.model_dump() for item in metadata_items], ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        self._knowledge_manager.get_manifest_file(version).write_text(
            manifest.model_dump_json(indent=2),
            encoding="utf-8",
        )
        logger.info(
            "finish building rag index, version=%s, rawDocCount=%s, indexedDocCount=%s, dimension=%s",
            version,
            len(raw_chunks),
            len(chunks),
            vector_dimension,
        )
        return BuildIndexResponse(
            version=version,
            status="ready",
            manifest=manifest,
            message=f"索引构建完成，原始分块 {len(raw_chunks)} 条，进入索引 {len(chunks)} 条，尚未切换为激活版本。",
        )

    def _build_metadata(self, row_id: int, chunk: RagChunk) -> RagChunkMetadata:
        """把进入索引的 chunk 转成 metadata 行。

        FAISS 索引本身只保存向量，不保存原始文本和业务字段。
        因此必须额外维护一份 row_id -> 原始 chunk 信息的映射文件，
        检索结果才能再反查回标题、页码、章节和正文。
        """

        meta = dict(chunk.metadata)
        return RagChunkMetadata(
            row_id=row_id,
            chunk_id=chunk.id,
            doc_id=str(meta.get("doc_id") or ""),
            chunk_type=str(meta.get("chunk_type") or ""),
            text=chunk.text,
            title=chunk.title,
            source=chunk.source,
            category=chunk.category,
            tags=chunk.tags,
            page_start=self._to_int(meta.get("page_start")),
            page_end=self._to_int(meta.get("page_end")),
            part_title=str(meta.get("part_title") or ""),
            chapter_title=str(meta.get("chapter_title") or ""),
            section_title=str(meta.get("section_title") or ""),
            subtopic_title=str(meta.get("subtopic_title") or ""),
            quality_score=self._to_float(meta.get("quality_score")),
            metadata=meta,
        )

    def _build_embedding_text(self, chunk: RagChunk) -> str:
        """把章节信息和正文组合成更适合向量化的文本。

        这样检索时不仅能靠正文相似度命中，也能利用章节标题带来的主题信息。
        """

        meta = chunk.metadata
        headers = [
            str(meta.get("part_title") or ""),
            str(meta.get("chapter_title") or ""),
            str(meta.get("section_title") or ""),
            str(meta.get("subtopic_title") or ""),
        ]
        lines = [header for header in headers if header]
        lines.append(chunk.text)
        return "\n".join(lines)

    def _should_index(self, chunk: RagChunk) -> bool:
        """判断一个 chunk 是否值得进入索引。

        这里采用的是“轻量过滤”策略，而不是复杂清洗：
        - 不追求把所有噪声一次性清理干净；
        - 只先去掉最明显会伤害召回质量的片段。

        这样做的原因是：
        1. 首版规则更稳，误杀率更低；
        2. 当知识格式继续演进时，维护成本也更低。
        """

        text = chunk.text.strip()
        if len(text) < self._MIN_TEXT_LENGTH:
            return False

        quality_score = self._to_float(chunk.metadata.get("quality_score"))
        if quality_score is not None and quality_score < self._MIN_QUALITY_SCORE:
            return False

        # 过滤明显目录页样式：正文里目录符号占比过高，且几乎没有有效句子。
        # 这类 chunk 往往会在向量空间里形成无意义噪声，召回时却容易因为结构词命中而混入结果。
        dotted_noise = len(re.findall(r"[⋯·—\-_=]{3,}", text))
        if dotted_noise >= 3 and text.count("。") == 0:
            return False

        # 过滤极高比例的异常标记噪声。
        # 当前知识文件来自 OCR/排版切块时，偶尔会出现一串符号污染正文。
        weird_symbol_count = len(re.findall(r"[\"#$%&'()*+]+", text))
        if weird_symbol_count > max(20, len(text) // 15):
            return False

        return True

    def _to_float(self, value: object) -> float | None:
        try:
            return float(value) if value is not None else None
        except (TypeError, ValueError):
            return None

    def _to_int(self, value: object) -> int | None:
        try:
            return int(value) if value is not None else None
        except (TypeError, ValueError):
            return None

    def _resolve_path(self, path_text: str) -> Path:
        path = Path(path_text)
        return path if path.is_absolute() else BASE_DIR / path
