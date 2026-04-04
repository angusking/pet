"""FastAPI 应用主入口。

这个文件只负责：
1. 初始化全局配置
2. 初始化日志
3. 在应用启动/关闭时准备共享依赖
4. 注册 HTTP 路由

真正的聊天业务流程不写在这里，而是交给 ChatOrchestrator。
这样做的好处是：
- 启动逻辑和业务逻辑分离
- 后续更容易测试编排器本身
- 替换 Web 框架时，业务核心不需要大改
"""

from contextlib import asynccontextmanager

from fastapi import FastAPI

from ai_service.api.kb_admin import router as kb_admin_router
from ai_service.api.chat import internal_router, router as chat_router
from ai_service.capabilities.rag_service import RagService
from ai_service.core.logging import configure_logging, get_logger
from ai_service.core.settings import get_settings
from ai_service.orchestrators.chat_orchestrator import ChatOrchestrator
from ai_service.providers.memory.redis_memory import RedisMemoryProvider
from ai_service.rag.embedding_provider import LocalEmbeddingProvider
from ai_service.rag.index_builder import IndexBuilder
from ai_service.rag.knowledge_manager import KnowledgeManager
from ai_service.rag.retriever import FaissRetriever

# 配置和日志在模块加载时初始化一次。
# 这样后续其他模块拿到的就是同一套配置对象和日志行为。
settings = get_settings()
configure_logging(settings.log_level, settings.log_dir)
logger = get_logger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """管理应用启动和关闭时的共享资源。

    FastAPI 会在启动时进入这个上下文，在关闭时退出这个上下文。
    适合放：
    - Redis 连接
    - 编排器实例
    - 其他全局共享资源
    """
    logger.info("AIService starting, env=%s, port=%s", settings.app_env, settings.app_port)

    # RedisMemoryProvider 负责最底层的 Redis 读写。
    # 编排器不会直接碰 Redis 客户端，而是通过 provider 和 service 间接访问。
    memory_provider = RedisMemoryProvider(settings)
    await memory_provider.connect()

    knowledge_manager = None
    index_builder = None
    rag_service = None

    # 只有显式开启 RAG 时，才初始化知识库、向量检索和索引构建相关组件。
    # 这样在排障或轻量部署场景下，RAG 关闭后不会再被 embedding / FAISS 初始化阻塞启动。
    if settings.rag_enabled:
        knowledge_manager = KnowledgeManager(settings=settings)
        embedding_provider = LocalEmbeddingProvider(settings=settings)
        retriever = FaissRetriever(
            knowledge_manager=knowledge_manager,
            embedding_provider=embedding_provider,
        )
        index_builder = IndexBuilder(
            knowledge_manager=knowledge_manager,
            embedding_provider=embedding_provider,
        )
        rag_service = RagService(
            retriever=retriever,
            top_k=settings.rag_top_k,
            enabled=True,
        )

        if settings.rag_auto_load_on_start:
            try:
                loaded_version = retriever.load_active()
                if loaded_version:
                    logger.info("RAG active version loaded on startup, version=%s", loaded_version)
                else:
                    logger.info("RAG has no active version on startup")
            except Exception as exc:
                logger.warning("RAG auto load failed on startup, error=%s", exc)
    else:
        logger.info("RAG disabled by configuration, skip retriever initialization")

    orchestrator = ChatOrchestrator(
        settings=settings,
        memory_provider=memory_provider,
        rag_service=rag_service,
    )

    # 把共享对象挂到 app.state 上，供路由层读取。
    app.state.memory_provider = memory_provider
    app.state.chat_orchestrator = orchestrator
    app.state.knowledge_manager = knowledge_manager
    app.state.index_builder = index_builder
    app.state.rag_service = rag_service

    yield

    logger.info("AIService shutting down")
    await memory_provider.close()


app = FastAPI(
    title="Pet AI Service",
    description="宠物社区 AI 编排服务",
    version="1.0.0",
    lifespan=lifespan,
)

app.include_router(chat_router)
app.include_router(internal_router)
app.include_router(kb_admin_router)


@app.get("/health")
async def health() -> dict[str, str]:
    """健康检查接口。

    这里只返回最基本的服务状态，方便部署和容器探针使用。
    """
    return {"status": "ok", "service": settings.app_name}
