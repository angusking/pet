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

from ai_service.api.chat import router as chat_router
from ai_service.core.logging import configure_logging, get_logger
from ai_service.core.settings import get_settings
from ai_service.orchestrators.chat_orchestrator import ChatOrchestrator
from ai_service.providers.memory.redis_memory import RedisMemoryProvider

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

    orchestrator = ChatOrchestrator(settings=settings, memory_provider=memory_provider)

    # 把共享对象挂到 app.state 上，供路由层读取。
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
    """健康检查接口。

    这里只返回最基本的服务状态，方便部署和容器探针使用。
    """
    return {"status": "ok", "service": settings.app_name}
