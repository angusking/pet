"""AIService 全局配置。"""

from functools import lru_cache
from pathlib import Path

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict

BASE_DIR = Path(__file__).resolve().parents[2]


class Settings(BaseSettings):
    """集中管理 AIService 的运行配置。"""

    model_config = SettingsConfigDict(
        env_file=str(BASE_DIR / ".env"),
        env_file_encoding="utf-8",
        extra="ignore",
    )

    app_name: str = Field(default="pet-ai-service", alias="APP_NAME")
    app_env: str = Field(default="dev", alias="APP_ENV")
    app_host: str = Field(default="0.0.0.0", alias="APP_HOST")
    app_port: int = Field(default=8001, alias="APP_PORT")
    log_level: str = Field(default="INFO", alias="LOG_LEVEL")

    # 本地从 D:/pet/AIService 启动时，默认会把日志写到 D:/pet/AIlog。
    log_dir: str = Field(default="../AIlog", alias="AI_LOG_DIR")

    redis_host: str = Field(default="localhost", alias="REDIS_HOST")
    redis_port: int = Field(default=6379, alias="REDIS_PORT")
    redis_db: int = Field(default=0, alias="REDIS_DB")

    # 这组配置只在 Redis 和 recentMessages 都不可用时，回源 backend 历史消息使用。
    backend_base_url: str = Field(default="http://localhost:8080", alias="BACKEND_BASE_URL")
    backend_timeout_seconds: int = Field(default=5, alias="BACKEND_TIMEOUT_SECONDS")

    # 第一阶段短期记忆策略：
    # 最多保留最近 8 条消息，TTL 为 24 小时。
    memory_max_messages: int = Field(default=8, alias="MEMORY_MAX_MESSAGES")
    memory_ttl_seconds: int = Field(default=86400, alias="MEMORY_TTL_SECONDS")

    dashscope_api_key: str = Field(default="", alias="DASHSCOPE_API_KEY")
    dashscope_base_url: str = Field(default="", alias="DASHSCOPE_BASE_URL")
    qwen_model: str = Field(default="qwen-plus", alias="QWEN_MODEL")
    qwen_timeout_seconds: int = Field(default=30, alias="QWEN_TIMEOUT_SECONDS")

    system_prompt_file: str = Field(
        default="./ai_service/prompts/system_prompt.md",
        alias="SYSTEM_PROMPT_FILE",
    )


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    """返回带缓存的单例配置对象。"""
    return Settings()
