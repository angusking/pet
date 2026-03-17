"""全局配置管理。

新手最容易踩的坑是：
- 从不同目录启动 Python，导致 `.env` 读不到
- 配置分散在很多模块里，不知道到底哪里在生效

所以这里把所有配置统一收口到一个 Settings 类里。
"""

from functools import lru_cache
from pathlib import Path

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict

# `settings.py` 位于 `AIService/ai_service/core/` 下。
# parents[2] 正好回到 `AIService/` 根目录，这样不管你从哪里启动，
# 都能稳定读到 `AIService/.env`。
BASE_DIR = Path(__file__).resolve().parents[2]


class Settings(BaseSettings):
    """AIService 全局配置。

    BaseSettings 会自动从环境变量和 `.env` 文件读取值。
    代码里只需要依赖这个对象，而不用到处自己读取环境变量。
    """

    model_config = SettingsConfigDict(
        env_file=str(BASE_DIR / ".env"),
        env_file_encoding="utf-8",
        extra="ignore",
    )

    # 应用自身配置
    app_name: str = Field(default="pet-ai-service", alias="APP_NAME")
    app_env: str = Field(default="dev", alias="APP_ENV")
    app_host: str = Field(default="0.0.0.0", alias="APP_HOST")
    app_port: int = Field(default=8001, alias="APP_PORT")
    log_level: str = Field(default="INFO", alias="LOG_LEVEL")

    # AI 对话日志目录。
    # 本地默认写到仓库根目录 `D:/pet/AIlog`，容器部署时会被环境变量覆盖成 `/app/AIlog`。
    log_dir: str = Field(default="../AIlog", alias="AI_LOG_DIR")

    # Redis 短期记忆配置
    redis_host: str = Field(default="localhost", alias="REDIS_HOST")
    redis_port: int = Field(default=6379, alias="REDIS_PORT")
    redis_db: int = Field(default=0, alias="REDIS_DB")

    # 这里的 max / ttl 对应第一阶段短期记忆策略：
    # - 最多保留最近 8 条消息
    # - TTL 24 小时
    memory_max_messages: int = Field(default=8, alias="MEMORY_MAX_MESSAGES")
    memory_ttl_seconds: int = Field(default=86400, alias="MEMORY_TTL_SECONDS")

    # 大模型提供方配置
    dashscope_api_key: str = Field(default="", alias="DASHSCOPE_API_KEY")
    dashscope_base_url: str = Field(default="", alias="DASHSCOPE_BASE_URL")
    qwen_model: str = Field(default="qwen-plus", alias="QWEN_MODEL")
    qwen_timeout_seconds: int = Field(default=30, alias="QWEN_TIMEOUT_SECONDS")

    # 提示词文件路径。这里保留成配置项，是为了以后更换提示词时不需要改代码。
    system_prompt_file: str = Field(
        default="./ai_service/prompts/system_prompt.md",
        alias="SYSTEM_PROMPT_FILE",
    )


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    """返回单例配置对象。

    用缓存的原因：
    - 避免每次都重新解析环境变量
    - 保证整个进程里拿到的是同一份配置视图
    """
    return Settings()
