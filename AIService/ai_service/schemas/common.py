"""公共 Schema 定义。"""

from pydantic import BaseModel, Field


class ServiceItem(BaseModel):
    """推荐服务项。"""

    name: str = Field(..., description="服务名称")
    description: str = Field(default="", description="服务说明")
    url: str = Field(default="", description="服务地址")


class ActionCard(BaseModel):
    """前端可直接渲染的建议卡片。"""

    title: str = Field(default="", description="卡片标题")
    items: list[str] = Field(default_factory=list, description="卡片内容项")
