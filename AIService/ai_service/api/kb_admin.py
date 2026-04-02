"""本地知识库管理接口。

这组接口不是面向普通聊天用户的，而是面向开发、运维或调试阶段。
它的作用是把“知识版本管理”这件事变成显式、可操作的后台能力：

- 查当前激活版本；
- 查所有版本状态；
- 构建新版本索引；
- 切换当前生效版本并热加载。
"""

from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Request

from ai_service.capabilities.rag_service import RagService
from ai_service.rag.exceptions import (
    IndexBuildError,
    KnowledgeVersionInvalidError,
    KnowledgeVersionNotFoundError,
    RagError,
)
from ai_service.rag.index_builder import IndexBuilder
from ai_service.rag.knowledge_manager import KnowledgeManager
from ai_service.rag.schemas import (
    BuildIndexRequest,
    BuildIndexResponse,
    CurrentVersionResponse,
    KnowledgeVersionInfo,
    SwitchVersionRequest,
)

router = APIRouter(prefix="/kb", tags=["kb-admin"])


def get_knowledge_manager(request: Request) -> KnowledgeManager:
    """从应用上下文获取 KnowledgeManager。"""

    return request.app.state.knowledge_manager


def get_index_builder(request: Request) -> IndexBuilder:
    """从应用上下文获取 IndexBuilder。"""

    return request.app.state.index_builder


def get_rag_service(request: Request) -> RagService:
    """从应用上下文获取 RagService。"""

    return request.app.state.rag_service


@router.get("/current", response_model=CurrentVersionResponse)
async def current_version(
    knowledge_manager: KnowledgeManager = Depends(get_knowledge_manager),
    rag_service: RagService = Depends(get_rag_service),
) -> CurrentVersionResponse:
    """查询当前激活版本和在线已加载版本。"""

    return knowledge_manager.get_current_status(loaded_version=rag_service.current_version())


@router.get("/versions", response_model=list[KnowledgeVersionInfo])
async def list_versions(
    knowledge_manager: KnowledgeManager = Depends(get_knowledge_manager),
) -> list[KnowledgeVersionInfo]:
    """列出所有版本及状态。"""

    return knowledge_manager.list_versions()


@router.post("/rebuild", response_model=BuildIndexResponse)
async def rebuild_version(
    payload: BuildIndexRequest,
    index_builder: IndexBuilder = Depends(get_index_builder),
) -> BuildIndexResponse:
    """为指定版本构建新的 FAISS 索引。

    当前设计是“只构建，不自动切换”，
    这样构建成功后仍然需要显式调用 `/kb/switch` 才会生效。
    """

    try:
        return index_builder.build(
            version=payload.version,
            knowledge_file_path=payload.knowledge_file_path,
        )
    except IndexBuildError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@router.post("/switch")
async def switch_version(
    payload: SwitchVersionRequest,
    knowledge_manager: KnowledgeManager = Depends(get_knowledge_manager),
    rag_service: RagService = Depends(get_rag_service),
) -> dict[str, str]:
    """切换当前激活版本，并立即触发 Retriever 热加载。

    这里故意把“写 active_kb.json”和“在线重载 Retriever”放在同一个接口里，
    目的是让版本切换对外表现为一次原子操作：
    - 磁盘状态切过去；
    - 在线检索也立刻切过去。

    如果重载失败，会尽量回滚到旧版本，减少“磁盘已切换但进程内还没切换”的不一致窗口。
    """

    previous_active_version = knowledge_manager.get_active_version()
    try:
        knowledge_manager.set_active_version(payload.version)
        rag_service.reload(payload.version)
    except (KnowledgeVersionNotFoundError, KnowledgeVersionInvalidError) as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except RagError as exc:
        # active_version 已经写入但热加载失败时，尽量把状态回滚到旧版本，
        # 避免磁盘状态和在线 Retriever 状态长期不一致。
        if previous_active_version and previous_active_version != payload.version:
            try:
                knowledge_manager.set_active_version(previous_active_version)
                rag_service.reload(previous_active_version)
            except Exception:
                pass
        raise HTTPException(status_code=500, detail=str(exc)) from exc

    return {
        "status": "ok",
        "activeVersion": payload.version,
        "loadedVersion": rag_service.current_version() or "",
    }
