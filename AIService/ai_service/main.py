"""FastAPI 应用主入口。

这里负责创建应用对象、初始化共享依赖并注册路由。
业务处理本身不放在这里，而是交给独立的编排器模块。
"""

from contextlib import asynccontextmanager

from fastapi import FastAPI

from ai_service.api.chat import router as chat_router
from ai_service.core.logging import configure_logging, get_logger
from ai_service.core.settings import get_settings
from ai_service.orchestrators.chat_orchestrator import ChatOrchestrator
from ai_service.providers.memory.redis_memory import RedisMemoryProvider

settings = get_settings()
configure_logging(settings.log_level, settings.log_dir)
logger = get_logger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """管理应用启动和关闭时的共享资源。"""
    logger.info("AIService starting, env=%s, port=%s", settings.app_env, settings.app_port)

    memory_provider = RedisMemoryProvider(settings)
    await memory_provider.connect()

    orchestrator = ChatOrchestrator(settings=settings, memory_provider=memory_provider)

    app.state.memory_provider = memory_provider
    app.state.chat_orchestrator = orchestrator

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


@app.get("/health")
async def health() -> dict[str, str]:
    """健康检查接口。"""
    return {"status": "ok", "service": settings.app_name}
