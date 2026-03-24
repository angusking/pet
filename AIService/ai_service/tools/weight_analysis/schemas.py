"""体重分析 Tool 的输入输出结构。"""

from typing import Literal

from pydantic import BaseModel, Field


class WeightAnalysisInput(BaseModel):
    """体重分析 Tool 输入。"""

    petId: int = Field(..., description="要分析的宠物 ID")
    userId: int = Field(..., description="当前用户 ID，用于后端接口归属校验")


class WeightAnalysisResult(BaseModel):
    """体重分析 Tool 输出。"""

    tool: str = Field(default="weight_analysis", description="Tool 名称")
    status: Literal["success", "no_data"] = Field(..., description="执行状态")
    petId: int = Field(..., description="宠物 ID")
    supportLevel: str = Field(default="trend_only", description="类别支持级别")
    categoryWeightHint: str = Field(default="", description="分类参考提示")
    recordCount: int = Field(default=0, description="参与分析的记录条数")
    currentWeight: float | None = Field(default=None, description="最近一次体重")
    previousWeight: float | None = Field(default=None, description="前一次体重")
    changeFromPrevious: float | None = Field(default=None, description="较前一次变化")
    trend: Literal["up", "down", "stable", "unknown"] = Field(
        default="unknown",
        description="趋势方向",
    )
    analysis: str = Field(default="", description="对用户可读的一句话分析结果")
    observations: list[str] = Field(default_factory=list, description="补充观察点")
    riskHint: str = Field(default="", description="谨慎提示")
