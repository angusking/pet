"""聊天请求模型。

这些 schema 的价值不只是“定义字段”，更重要的是：
- 统一请求契约
- 自动校验输入
- 让编排器拿到结构稳定的数据
"""

from typing import Any

from pydantic import BaseModel, Field


class PetInfo(BaseModel):
    """宠物基础信息。

    这里只放当前轮对话真正需要的轻量字段，
    不把完整宠物档案都塞进来，避免请求体过大。
    """

    petId: int = Field(..., description="宠物 ID")
    name: str = Field(..., description="宠物名称")
    type: str = Field(default="", description="宠物类型，例如 cat / dog")
    age: float | None = Field(default=None, description="宠物年龄，单位年")
    weight: float | None = Field(default=None, description="宠物体重，单位 kg")


class RecentMessage(BaseModel):
    """最近对话消息。

    这是 backend 在第一阶段给 AIService 的兜底上下文。
    当 Redis 里查不到短期记忆时，AIService 会用它恢复上下文。
    """

    role: str = Field(..., description="消息角色，例如 user / assistant")
    content: str = Field(..., description="消息内容")


class BizData(BaseModel):
    """业务补充数据。

    这部分不是聊天历史，而是给工具调用或回答增强使用的业务数据。
    """

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
