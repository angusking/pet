"""AIService 启动入口。

这个文件只负责读取配置并启动 Uvicorn。
应用生命周期、依赖初始化和业务路由定义都放在 `ai_service.main` 中。
"""

import uvicorn

from ai_service.core.settings import get_settings


def main() -> None:
    """读取配置并启动服务。"""
    settings = get_settings()
    uvicorn.run(
        "ai_service.main:app",
        host=settings.app_host,
        port=settings.app_port,
        reload=settings.app_env == "dev",
    )


if __name__ == "__main__":
    main()
