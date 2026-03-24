"""聊天响应模型。"""

from enum import Enum

from pydantic import BaseModel, Field

from ai_service.schemas.common import ActionCard, ServiceItem


class RiskLevel(str, Enum):
    """统一风险等级。"""

    LOW = "low"
    MEDIUM = "medium"
    HIGH = "high"


class ChatResponse(BaseModel):
    """AIService 标准响应。

    这里把结构化展示字段一起返回，供 Java 后端直接透传给前端。
    """

    requestId: str = Field(..., description="请求唯一标识")
    intent: str = Field(default="UNKNOWN", description="回答意图标签")
    answer: str = Field(..., description="AI 主回答")
    riskLevel: RiskLevel = Field(default=RiskLevel.LOW, description="风险等级")
    checklist: list[str] = Field(default_factory=list, description="建议清单")
    services: list[ServiceItem] = Field(default_factory=list, description="推荐服务")
    followUps: list[str] = Field(default_factory=list, description="建议追问")
    followUpQuestions: list[str] = Field(default_factory=list, description="前端快捷追问")
    actionCards: list[ActionCard] = Field(default_factory=list, description="建议卡片")
    disclaimer: str = Field(
        default="本回答仅供宠物日常养护参考，不能替代执业兽医诊断。",
        description="免责声明",
    )
