"""聊天响应模型。"""

from enum import Enum

from pydantic import BaseModel, Field

from ai_service.schemas.common import ServiceItem


class RiskLevel(str, Enum):
    """风险等级枚举。"""

    LOW = "low"
    MEDIUM = "medium"
    HIGH = "high"


class ChatResponse(BaseModel):
    """AIService 标准响应。"""

    requestId: str = Field(..., description="请求唯一标识")
    answer: str = Field(..., description="AI 主回答")
    riskLevel: RiskLevel = Field(default=RiskLevel.LOW, description="风险等级")
    checklist: list[str] = Field(default_factory=list, description="建议检查清单")
    services: list[ServiceItem] = Field(default_factory=list, description="推荐服务")
    followUps: list[str] = Field(default_factory=list, description="建议追问")
    disclaimer: str = Field(
        default="本回答仅供宠物日常护理参考，不能替代执业兽医诊断。",
        description="免责声明",
    )
