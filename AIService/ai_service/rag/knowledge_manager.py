"""知识库版本管理器。"""

from __future__ import annotations

import json
from pathlib import Path

from ai_service.core.logging import get_logger
from ai_service.core.settings import BASE_DIR, Settings
from ai_service.rag.exceptions import KnowledgeVersionInvalidError, KnowledgeVersionNotFoundError
from ai_service.rag.schemas import CurrentVersionResponse, IndexManifest, KnowledgeVersionInfo

logger = get_logger(__name__)


class KnowledgeManager:
    """管理知识库版本目录、active_version 和完整性校验。

    这一层只关心“版本状态”，不关心 embedding、FAISS 检索或 Prompt。
    可以把它理解成 RAG 子系统里的“版本仓库管理员”：

    1. 知道知识文件放在哪里；
    2. 知道索引文件放在哪里；
    3. 知道当前 active_version 是谁；
    4. 能判断某个版本是否已经达到可检索状态。

    这样做的好处是把“版本管理”和“检索执行”解耦：
    - 检索器只需要关心如何读取一个 ready 版本；
    - 构建器只需要关心如何产出一个 ready 版本；
    - API 层只需要调用这里提供的状态判断方法。
    """

    _KNOWLEDGE_FILE_NAME = "rag_chunks.jsonl"
    _INDEX_FILE_NAME = "faiss.index"
    _METADATA_FILE_NAME = "metadata.json"
    _MANIFEST_FILE_NAME = "manifest.json"

    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._knowledge_root = self._resolve_path(settings.rag_knowledge_dir)
        self._index_root = self._resolve_path(settings.rag_index_dir)
        self._active_file = self._resolve_path(settings.rag_active_file)
        # 启动时保证目录存在，避免后续每次构建或切换前都重复判断。
        self._knowledge_root.mkdir(parents=True, exist_ok=True)
        self._index_root.mkdir(parents=True, exist_ok=True)
        self._active_file.parent.mkdir(parents=True, exist_ok=True)

    def get_active_version(self) -> str | None:
        """读取当前激活版本。

        如果 active 文件不存在，说明还没有任何版本被激活。
        """

        if not self._active_file.exists():
            return None
        try:
            payload = json.loads(self._active_file.read_text(encoding="utf-8"))
        except Exception:
            logger.warning("active kb file is broken: %s", self._active_file)
            return None
        version = payload.get("active_version")
        return str(version).strip() if version else None

    def get_current_status(self, loaded_version: str | None) -> CurrentVersionResponse:
        """返回当前激活版本与检索器加载状态。"""

        active_version = self.get_active_version()
        if not active_version:
            return CurrentVersionResponse(active_version=None, loaded_version=loaded_version, status="empty")
        version_info = self._build_version_info(active_version)
        return CurrentVersionResponse(
            active_version=active_version,
            loaded_version=loaded_version,
            status=version_info.status,
        )

    def set_active_version(self, version: str) -> None:
        """切换 active_version。

        注意这里只写激活版本文件，不负责热加载检索器。
        热加载由上层在写成功后显式触发。
        """

        self.ensure_ready_version(version)
        self._active_file.write_text(
            json.dumps({"active_version": version}, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        logger.info("set active knowledge version, version=%s", version)

    def list_versions(self) -> list[KnowledgeVersionInfo]:
        """列出所有版本及状态。

        版本集合取 knowledge 目录和 indexes 目录的并集，这样：
        - 只有知识文件没有索引的版本也能看到；
        - 构建残留的 broken 版本也能暴露出来。
        """

        active_version = self.get_active_version()
        versions = {
            path.name for path in self._knowledge_root.iterdir() if path.is_dir()
        } | {
            path.name for path in self._index_root.iterdir() if path.is_dir()
        }
        # 这里返回的是聚合视图，而不是简单目录名列表。
        # API 层可以直接把结果透出给前端或运维接口，不需要再做二次拼装。
        return [
            self._build_version_info(version_name, is_active=(version_name == active_version))
            for version_name in sorted(versions)
        ]

    def ensure_ready_version(self, version: str) -> None:
        """确保指定版本存在且索引完整。"""

        info = self._build_version_info(version)
        if not info.knowledge_exists and not info.index_exists:
            raise KnowledgeVersionNotFoundError(f"知识库版本不存在: {version}")
        if info.status != "ready":
            raise KnowledgeVersionInvalidError(f"知识库版本未准备完成: {version}, status={info.status}")

    def get_knowledge_file(self, version: str) -> Path:
        """返回指定版本默认知识文件路径。"""

        return self._knowledge_root / version / self._KNOWLEDGE_FILE_NAME

    def get_index_dir(self, version: str) -> Path:
        """返回指定版本索引目录。"""

        return self._index_root / version

    def get_index_file(self, version: str) -> Path:
        """返回指定版本 FAISS 索引文件路径。"""

        return self.get_index_dir(version) / self._INDEX_FILE_NAME

    def get_metadata_file(self, version: str) -> Path:
        """返回指定版本 metadata 文件路径。"""

        return self.get_index_dir(version) / self._METADATA_FILE_NAME

    def get_manifest_file(self, version: str) -> Path:
        """返回指定版本 manifest 文件路径。"""

        return self.get_index_dir(version) / self._MANIFEST_FILE_NAME

    def _build_version_info(self, version: str, is_active: bool = False) -> KnowledgeVersionInfo:
        """汇总单个版本的文件存在性与运行状态。

        当前状态定义是：
        - ready：知识文件和索引文件都完整；
        - knowledge_only：只有知识文件，尚未构建索引；
        - broken：索引文件残缺，或者只剩部分构建产物；
        - unknown：既没有知识文件也没有索引目录，通常只会在脏目录扫描时出现。
        """

        knowledge_file = self.get_knowledge_file(version)
        index_file = self.get_index_file(version)
        metadata_file = self.get_metadata_file(version)
        manifest_file = self.get_manifest_file(version)

        knowledge_exists = knowledge_file.exists()
        index_exists = index_file.exists() and metadata_file.exists() and manifest_file.exists()
        manifest = self._read_manifest(manifest_file) if manifest_file.exists() else None

        if knowledge_exists and index_exists:
            status = "ready"
        elif knowledge_exists and not index_exists:
            status = "knowledge_only"
        elif not knowledge_exists and (index_file.exists() or metadata_file.exists() or manifest_file.exists()):
            status = "broken"
        else:
            status = "unknown"

        return KnowledgeVersionInfo(
            version=version,
            knowledge_exists=knowledge_exists,
            index_exists=index_exists,
            status=status,
            is_active=is_active,
            manifest=manifest,
        )

    def _read_manifest(self, path: Path) -> IndexManifest | None:
        """读取 manifest。

        manifest 本身不是检索必需文件，但它对运维排查很有帮助：
        - 知道当前索引是何时构建的；
        - 知道用了哪个 embedding 模型；
        - 知道进入索引的 chunk 数量。
        """

        try:
            payload = json.loads(path.read_text(encoding="utf-8"))
            return IndexManifest.model_validate(payload)
        except Exception:
            logger.warning("manifest file is broken: %s", path)
            return None

    def _resolve_path(self, path_text: str) -> Path:
        path = Path(path_text)
        return path if path.is_absolute() else BASE_DIR / path
