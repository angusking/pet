"""Question Rewrite 前置模块的结构化输出。"""

from enum import Enum
from typing import Any

from pydantic import BaseModel, Field


class RewriteIntent(str, Enum):
    """当前版本支持的重写意图类型。"""

    WEIGHT_ANALYSIS = "weight_analysis"
    WEIGHT_FOLLOW_UP = "weight_follow_up"
    LOCATION_SEARCH = "location_search"
    GENERAL_KNOWLEDGE = "general_knowledge"
    UNKNOWN = "unknown"


class RewriteSource(str, Enum):
    """重写结果来源。"""

    RULE = "rule"
    LLM = "llm"
    HYBRID = "hybrid"
    FALLBACK = "fallback"


class QuestionRewriteResult(BaseModel):
    """Question Rewrite 前置模块输出。

    这层只负责“把问题看明白”，不负责直接回答用户，也不直接执行 Tool。
    后续 DecisionService、RAG 和 Tool Router 会基于它继续往下做决策。
    """

    originalQuestion: str = Field(..., description="用户原始问题")
    normalizedQuestion: str = Field(..., description="标准化后的问题描述")
    intentType: RewriteIntent = Field(default=RewriteIntent.UNKNOWN, description="意图类型")
    suggestTool: bool = Field(default=False, description="是否建议后续优先考虑工具")
    suggestedToolName: str | None = Field(default=None, description="建议的工具名称")
    followUp: bool = Field(default=False, description="是否属于追问或补充观察信息")
    needKnowledgeRetrieval: bool = Field(default=False, description="是否可能需要后续知识检索")
    confidence: float = Field(default=0.0, description="当前结果置信度，取值 0 到 1")
    source: RewriteSource = Field(default=RewriteSource.FALLBACK, description="结果来源")
    reasoningTags: list[str] = Field(default_factory=list, description="命中规则或判断标签")
    extractedSlots: dict[str, Any] = Field(default_factory=dict, description="解析出的结构化槽位")
