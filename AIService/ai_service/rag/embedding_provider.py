"""本地 embedding Provider。

当前默认使用 sentence-transformers 加载本地或 Hugging Face 模型。
这样既能满足“本地 RAG”的要求，也保留了未来替换模型的空间。
"""

from __future__ import annotations

from typing import Iterable

from ai_service.core.logging import get_logger
from ai_service.core.settings import Settings
from ai_service.rag.exceptions import IndexBuildError

logger = get_logger(__name__)


class LocalEmbeddingProvider:
    """本地 embedding 提供者。

    这里采用懒加载，避免：
    - 服务启动时就把模型全量加载进内存；
    - 没有真的用到 RAG 时增加不必要开销。
    """

    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._model = None

    def model_name(self) -> str:
        """返回当前 embedding 模型标识。"""

        return self._settings.rag_embedding_model_path or self._settings.rag_embedding_model

    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        """对文档分块做批量向量化。"""

        return self._encode(texts)

    def embed_query(self, text: str) -> list[float]:
        """对单条查询做向量化。"""

        vectors = self._encode([text])
        return vectors[0]

    def _encode(self, texts: Iterable[str]) -> list[list[float]]:
        """统一的编码入口。

        无论是构建索引还是在线查询，最终都会走到这里。
        这样做的意义是：
        - 批量文档编码和单条 query 编码共享同一套模型配置；
        - 如果后面要加日志、批大小控制、异常兜底，只需要改一个地方。
        """

        model = self._get_model()
        values = list(texts)
        if not values:
            return []
        try:
            embeddings = model.encode(
                values,
                normalize_embeddings=True,
                show_progress_bar=False,
            )
        except Exception as exc:
            raise IndexBuildError(f"embedding 生成失败: {exc}") from exc
        return embeddings.tolist()

    def _get_model(self):
        """懒加载并缓存 embedding 模型。

        首次真正需要 embedding 时才加载模型，后续重复复用同一个实例。
        这对本地模型尤其重要，否则服务一启动就会占用较多内存和启动时间。
        """

        if self._model is not None:
            return self._model

        # 这里把 sentence-transformers 的导入也延迟到真正需要 embedding 的时刻。
        # 否则即使 RAG 已关闭，只要模块被 import，就可能把 torch 等大依赖提前拉进内存，
        # 在 2G 机器上非常容易触发 OOM。
        try:
            from sentence_transformers import SentenceTransformer
        except Exception as exc:  # pragma: no cover - 运行时缺依赖时走清晰报错即可
            raise IndexBuildError(
                "未安装 sentence-transformers，无法加载本地 embedding 模型。"
            ) from exc

        model_name = self.model_name()
        logger.info("load local embedding model, model=%s", model_name)
        try:
            self._model = SentenceTransformer(model_name)
        except Exception as exc:
            raise IndexBuildError(f"加载 embedding 模型失败: {model_name}") from exc
        return self._model
