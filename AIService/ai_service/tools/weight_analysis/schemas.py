"""体重分析 Tool 的输入输出结构。"""

from typing import Literal

from pydantic import BaseModel, Field


class WeightAnalysisInput(BaseModel):
    """体重分析 Tool 输入。"""

    petId: int = Field(..., description="要分析的宠物 ID")
    userId: int = Field(..., description="当前用户 ID")
    requestId: str | None = Field(default=None, description="当前对话请求 ID")
    userMessage: str | None = Field(default=None, description="用户原始问题")


class WeightRecordItem(BaseModel):
    """供 Tool 内部传给 LLM 的体重记录条目。"""

    recordedAt: str
    weightValue: float
    unit: str
    source: str | None = None
    note: str | None = None
    deltaFromPrevious: float | None = None


class WeightAnalysisContext(BaseModel):
    """体重分析 Tool 送给 LLM 的整理后上下文。"""

    petId: int
    petName: str = ""
    categoryPath: str | None = None
    displaySpecies: str | None = None
    birthDate: str | None = None
    gender: str | None = None
    neutered: bool | None = None
    currentWeight: float | None = None
    latestRecordedAt: str | None = None
    recordCount: int = 0
    insufficientData: bool = False
    records: list[WeightRecordItem] = Field(default_factory=list)


class WeightAnalysisResult(BaseModel):
    """体重分析 Tool 输出。"""

    tool: str = Field(default="weight_analysis", description="Tool 名称")
    status: Literal["success", "no_data"] = Field(..., description="执行状态")
    petId: int = Field(..., description="宠物 ID")
    summary: str = Field(default="", description="一句话摘要")
    trend: Literal["up", "down", "stable", "unknown"] = Field(default="unknown")
    recordCount: int = Field(default=0, description="记录条数")
    currentWeight: float | None = Field(default=None, description="当前体重")
    latestRecordedAt: str | None = Field(default=None, description="最近记录时间")
    observations: list[str] = Field(default_factory=list, description="观察点")
    advice: list[str] = Field(default_factory=list, description="建议项")
    followUpQuestion: str = Field(default="", description="建议继续追问的问题")
    disclaimer: str = Field(default="", description="免责声明")
