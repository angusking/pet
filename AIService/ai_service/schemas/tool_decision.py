"""第一轮工具决策结果结构。"""

from pydantic import BaseModel, Field

from ai_service.schemas.chat_response import RiskLevel
from ai_service.schemas.common import ActionCard, ServiceItem


class ToolDecision(BaseModel):
    """第一轮内部决策输出。"""

    needTool: bool = Field(..., description="是否需要调用 Tool")
    toolName: str | None = Field(default=None, description="要调用的 Tool 名称")
    toolInput: dict | None = Field(default=None, description="Tool 输入")
    intent: str = Field(default="UNKNOWN", description="回答意图标签")
    answer: str = Field(default="", description="占位说明或直接返回给用户的答案")
    riskLevel: RiskLevel = Field(default=RiskLevel.LOW, description="风险等级")
    checklist: list[str] = Field(default_factory=list, description="建议清单")
    services: list[ServiceItem] = Field(default_factory=list, description="推荐服务")
    followUps: list[str] = Field(default_factory=list, description="追问项")
    followUpQuestions: list[str] = Field(default_factory=list, description="快捷追问")
    actionCards: list[ActionCard] = Field(default_factory=list, description="结构化卡片")
    disclaimer: str = Field(
        default="本回答仅供宠物日常养护参考，不能替代执业兽医诊断。",
        description="免责声明",
    )
