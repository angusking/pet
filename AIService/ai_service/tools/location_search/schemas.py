"""地点搜索 Tool 的输入输出结构。

首版地点搜索主要面向“某区域附近有什么宠物医院/门店”这类需求，
因此这里把输入拆成“地点描述”和“搜索关键词”两部分，方便后续：
1. 第一轮决策直接显式传参；
2. Tool 自己从 rewrite 结果里兜底提取；
3. 最终把第三方返回统一成稳定结构，供第二轮回答 Prompt 使用。
"""

from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field


class LocationSearchInput(BaseModel):
    """地点搜索 Tool 输入。

    - `location`：用户给出的区域、城市、商圈或地址片段。
    - `keyword`：用户想找的地点类型，例如“宠物医院”“宠物店”“洗护门店”。
    - `rewriteResult`：Question Rewrite 的结构化结果，当前 Tool 会用它做兜底解析。
    """

    userId: int | None = Field(default=None, description="当前用户 ID，首版主要用于日志排查。")
    requestId: str | None = Field(default=None, description="链路请求 ID。")
    userMessage: str = Field(default="", description="用户原始问题。")
    query: str | None = Field(default=None, description="决策层可选传入的标准化查询描述。")
    location: str | None = Field(default=None, description="地点或区域描述。")
    keyword: str | None = Field(default=None, description="要搜索的 POI 类型关键词。")
    rewriteResult: dict[str, Any] | None = Field(default=None, description="Question Rewrite 结果。")


class LocationSearchResultItem(BaseModel):
    """统一后的地点搜索结果项。"""

    name: str = Field(default="", description="地点名称。")
    address: str = Field(default="", description="完整地址描述。")
    region: str = Field(default="", description="省市区拼接后的区域信息。")
    location: str = Field(default="", description="经纬度，格式为 lng,lat。")
    tel: str = Field(default="", description="联系电话。")
    type: str = Field(default="", description="高德返回的 POI 类型描述。")


class LocationSearchResult(BaseModel):
    """地点搜索 Tool 输出。

    `status` 约定：
    - `success`：成功找到结果
    - `no_result`：请求成功，但没有匹配结果
    - `missing_location`：缺少可用地点信息
    - `error`：第三方调用失败或参数异常
    """

    tool: str = Field(default="location_search", description="Tool 名称。")
    status: str = Field(default="success", description="当前工具执行状态。")
    provider: str = Field(default="amap", description="第三方地图服务提供方。")
    location: str = Field(default="", description="本次检索使用的地点描述。")
    keyword: str = Field(default="", description="本次检索使用的关键词。")
    querySummary: str = Field(default="", description="供最终回答层直接引用的查询摘要。")
    resultCount: int = Field(default=0, description="命中的结果数量。")
    results: list[LocationSearchResultItem] = Field(default_factory=list, description="地点结果列表。")
    observations: list[str] = Field(default_factory=list, description="对本次检索结果的补充说明。")
    followUpQuestion: str = Field(default="", description="建议继续向用户确认的关键信息。")
    disclaimer: str = Field(
        default="地点结果来自第三方地图搜索，仅供参考，实际营业时间和服务范围请以门店信息为准。",
        description="地点搜索说明。",
    )

