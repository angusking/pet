"""聊天请求模型。"""

from typing import Any

from pydantic import BaseModel, Field


class PetInfo(BaseModel):
    """宠物基础信息。"""

    petId: int = Field(..., description="宠物 ID")
    name: str = Field(..., description="宠物名称")
    type: str = Field(default="", description="宠物类型，例如 cat / dog")
    age: float | None = Field(default=None, description="宠物年龄，单位为岁")
    weight: float | None = Field(default=None, description="宠物体重，单位为 kg")


class RecentMessage(BaseModel):
    """最近对话消息。"""

    role: str = Field(..., description="消息角色，例如 user / assistant")
    content: str = Field(..., description="消息内容")


class BizData(BaseModel):
    """业务补充数据。"""

    vaccines: list[Any] = Field(default_factory=list, description="疫苗记录")
    weightHistory: list[Any] = Field(default_factory=list, description="体重历史")


class ChatRequest(BaseModel):
    """AI 聊天请求主模型。"""

    requestId: str = Field(..., description="请求唯一标识")
    conversationId: str = Field(..., description="会话唯一标识")
    userId: int = Field(..., description="用户 ID")
    pet: PetInfo | None = Field(default=None, description="宠物信息")
    message: str = Field(..., min_length=1, description="当前用户输入")
    recentMessages: list[RecentMessage] = Field(default_factory=list, description="最近对话消息")
    bizData: BizData | None = Field(default=None, description="业务补充数据")
