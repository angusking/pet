"""AIService 全局配置。"""

from functools import lru_cache
from pathlib import Path

from pydantic import Field, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

BASE_DIR = Path(__file__).resolve().parents[2]


class Settings(BaseSettings):
    """集中管理 AIService 运行配置。"""

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

    # 本地从 D:/pet/AIService 启动时，默认把日志写到仓库根目录下的 AIlog。
    log_dir: str = Field(default="../AIlog", alias="AI_LOG_DIR")

    redis_host: str = Field(default="localhost", alias="REDIS_HOST")
    redis_port: int = Field(default=6379, alias="REDIS_PORT")
    redis_db: int = Field(default=0, alias="REDIS_DB")

    # 这组配置既用于历史消息兜底，也用于内部 Tool 调 Java 后端接口。
    backend_base_url: str = Field(default="http://localhost:8080", alias="BACKEND_BASE_URL")
    backend_timeout_seconds: int = Field(default=5, alias="BACKEND_TIMEOUT_SECONDS")

    # 第一阶段短期记忆策略：最多保留最近 8 条消息，TTL 为 24 小时。
    memory_max_messages: int = Field(default=8, alias="MEMORY_MAX_MESSAGES")
    memory_ttl_seconds: int = Field(default=86400, alias="MEMORY_TTL_SECONDS")

    dashscope_api_key: str = Field(default="", alias="DASHSCOPE_API_KEY")
    dashscope_base_url: str = Field(default="", alias="DASHSCOPE_BASE_URL")
    qwen_model: str = Field(default="qwen-plus", alias="QWEN_MODEL")
    qwen_timeout_seconds: int = Field(default=30, alias="QWEN_TIMEOUT_SECONDS")

    # 高德地图 Web Service 配置。地点搜索 Tool 直接访问第三方接口，
    # 这里集中管理 Key、地址和默认返回条数，避免在 Tool 内写死。
    amap_web_service_key: str = Field(default="", alias="AMAP_WEB_SERVICE_KEY")
    amap_base_url: str = Field(default="https://restapi.amap.com", alias="AMAP_BASE_URL")
    amap_search_page_size: int = Field(default=5, alias="AMAP_SEARCH_PAGE_SIZE")

    # Prompt 分层文件路径。
    base_system_prompt_file: str = Field(
        default="./ai_service/prompts/system/base_system_prompt.md",
        alias="BASE_SYSTEM_PROMPT_FILE",
    )
    decision_prompt_file: str = Field(
        default="./ai_service/prompts/system/decision_prompt.md",
        alias="DECISION_PROMPT_FILE",
    )
    question_rewrite_prompt_file: str = Field(
        default="./ai_service/prompts/system/question_rewrite_prompt.md",
        alias="QUESTION_REWRITE_PROMPT_FILE",
    )
    final_response_prompt_file: str = Field(
        default="./ai_service/prompts/system/final_response_prompt.md",
        alias="FINAL_RESPONSE_PROMPT_FILE",
    )
    tool_registry_prompt_file: str = Field(
        default="./ai_service/prompts/tools/tool_registry_prompt.md",
        alias="TOOL_REGISTRY_PROMPT_FILE",
    )
    weight_analysis_tool_prompt_file: str = Field(
        default="./ai_service/prompts/tools/weight_analysis_llm_prompt.md",
        alias="WEIGHT_ANALYSIS_TOOL_PROMPT_FILE",
    )

    # Tool 启用列表。当前默认启用体重分析和地点搜索，其它 Tool 继续保留扩展位。
    tool_enabled_list: list[str] = Field(
        default_factory=lambda: ["weight_analysis", "location_search"],
        alias="TOOL_ENABLED_LIST",
    )
    weight_analysis_limit: int = Field(default=10, alias="WEIGHT_ANALYSIS_LIMIT")

    @field_validator("tool_enabled_list", mode="before")
    @classmethod
    def parse_tool_enabled_list(cls, value: object) -> object:
        """兼容 JSON 数组和逗号分隔字符串两种配置写法。"""
        if isinstance(value, str):
            text = value.strip()
            if not text:
                return []
            if text.startswith("["):
                return value
            return [item.strip() for item in text.split(",") if item.strip()]
        return value


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    """返回带缓存的单例配置对象。"""
    return Settings()
