"""公共 Schema 定义。

这些模型会被多个请求/响应模型复用，所以单独放在 common 里。
"""

from pydantic import BaseModel, Field


class ServiceItem(BaseModel):
    """推荐服务项。

    这个结构给前端做服务卡片渲染用。
    """

    name: str = Field(..., description="服务名称")
    description: str = Field(default="", description="服务说明")
    url: str = Field(default="", description="服务地址")
