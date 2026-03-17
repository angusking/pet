"""全局配置管理。"""

from functools import lru_cache
from pathlib import Path

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict

BASE_DIR = Path(__file__).resolve().parents[2]


class Settings(BaseSettings):
    """AIService 全局配置。"""

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
    log_dir: str = Field(default="../AIlog", alias="AI_LOG_DIR")

    redis_host: str = Field(default="localhost", alias="REDIS_HOST")
    redis_port: int = Field(default=6379, alias="REDIS_PORT")
    redis_db: int = Field(default=0, alias="REDIS_DB")
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
    """返回单例配置对象。"""
    return Settings()
