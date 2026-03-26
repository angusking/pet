"""地点搜索 Tool 对外入口。

这一层负责三件事：
1. 从显式参数、rewrite 结果和原始问题里，尽量稳定地提取地点与关键词；
2. 调用高德文本搜索接口；
3. 把第三方返回归一成第二轮 Prompt 易消费的结果结构。

Tool 本身不直接给最终用户生成自然语言回答，这件事仍交给第二轮最终回答 Prompt。
"""

from __future__ import annotations

from typing import Any

from ai_service.core.exceptions import ToolInvocationError
from ai_service.core.logging import get_logger
from ai_service.core.settings import Settings
from ai_service.tools.base import BaseTool
from ai_service.tools.location_search.provider import AmapPlaceSearchProvider
from ai_service.tools.location_search.schemas import (
    LocationSearchInput,
    LocationSearchResult,
    LocationSearchResultItem,
)

logger = get_logger(__name__)


class LocationSearchTool(BaseTool):
    """基于高德文本搜索的地点查询 Tool。"""

    name = "location_search"

    _DEFAULT_KEYWORD = "宠物医院"
    _KEYWORD_CANDIDATES = (
        "24小时宠物医院",
        "宠物医院",
        "宠物诊所",
        "宠物店",
        "宠物门店",
        "宠物洗护",
        "洗护门店",
        "宠物寄养",
        "急诊医院",
        "医院",
        "门店",
    )
    _LOCATION_END_MARKERS = ("附近", "哪里", "在哪", "有没有", "有吗", "有", "能找到", "离我近")

    def __init__(self, settings: Settings) -> None:
        self._provider = AmapPlaceSearchProvider(settings=settings)

    async def run(self, payload: dict[str, Any]) -> dict[str, Any]:
        """执行地点搜索。

        当前策略比较保守：
        - 缺少明确地点：不发第三方请求，直接返回 `missing_location`
        - 关键词缺失：回退到“宠物医院”
        """

        tool_input = LocationSearchInput.model_validate(payload)
        location = self._resolve_location(tool_input)
        keyword = self._resolve_keyword(tool_input)

        if not location:
            logger.info("location search skipped because location is missing, requestId=%s", tool_input.requestId)
            return LocationSearchResult(
                status="missing_location",
                location="",
                keyword=keyword,
                querySummary="当前问题缺少明确地点信息，暂时无法执行地点搜索。",
                resultCount=0,
                observations=[
                    "地点搜索至少需要城市、区域、商圈或地址片段中的一种。",
                    "仅有“附近”这类相对描述时，当前版本无法可靠定位。",
                ],
                followUpQuestion="请告诉我你想查询的城市、区县、商圈或更具体的位置。",
            ).model_dump()

        try:
            raw_payload = await self._provider.search_text(keyword=keyword, region=location)
        except ToolInvocationError as exc:
            logger.warning(
                "location search failed, requestId=%s, location=%s, keyword=%s, error=%s",
                tool_input.requestId,
                location,
                keyword,
                exc,
            )
            return LocationSearchResult(
                status="error",
                location=location,
                keyword=keyword,
                querySummary=f"已尝试搜索“{location}”附近的“{keyword}”，但当前地图服务暂时不可用。",
                resultCount=0,
                observations=["第三方地图搜索失败，当前结果不可用。"],
                followUpQuestion="你可以稍后重试，或换一个更明确的地点再查一次。",
            ).model_dump()

        pois = raw_payload.get("pois") or []
        results = [self._normalize_poi(poi) for poi in pois if isinstance(poi, dict)]

        if not results:
            return LocationSearchResult(
                status="no_result",
                location=location,
                keyword=keyword,
                querySummary=f"已在“{location}”搜索“{keyword}”，但暂时没有找到合适结果。",
                resultCount=0,
                observations=[
                    "可以尝试缩小或放宽地点范围，例如从详细地址改成区县或商圈。",
                    "也可以更换搜索词，例如把“24小时宠物医院”改成“宠物医院”。",
                ],
                followUpQuestion="你希望我换一个更宽泛的地点或关键词再查一次吗？",
            ).model_dump()

        top_names = "、".join(item.name for item in results[:3] if item.name)
        return LocationSearchResult(
            status="success",
            location=location,
            keyword=keyword,
            querySummary=f"已在“{location}”搜索“{keyword}”，找到 {len(results)} 条候选结果。",
            resultCount=len(results),
            results=results,
            observations=[
                f"当前优先返回前 {len(results)} 条文本搜索结果。",
                f"最靠前的候选有：{top_names}。" if top_names else "高德返回了可用地点结果。",
            ],
            followUpQuestion="如果你需要，我可以继续帮你按营业时间、是否急诊或更具体区域再筛一轮。",
        ).model_dump()

    def _resolve_location(self, tool_input: LocationSearchInput) -> str:
        """从显式参数、rewrite 结果和原始问题中解析地点描述。"""

        candidates = [
            tool_input.location,
            self._slot_value(tool_input, "locationText"),
            self._extract_location_from_text(tool_input.userMessage),
        ]
        for candidate in candidates:
            normalized = self._clean_text(candidate)
            if normalized:
                return normalized
        return ""

    def _resolve_keyword(self, tool_input: LocationSearchInput) -> str:
        """解析要搜索的地点类型关键词。

        首版优先找显式关键词；如果用户只问“附近哪里有店”，会退回默认“宠物医院”。
        后续如果前端提供服务类型或用户当前位置，再把这套规则细化。
        """

        candidates = [
            tool_input.keyword,
            self._slot_value(tool_input, "placeKeyword"),
            self._extract_keyword_from_text(tool_input.userMessage),
        ]
        for candidate in candidates:
            normalized = self._clean_text(candidate)
            if normalized:
                return normalized
        return self._DEFAULT_KEYWORD

    def _slot_value(self, tool_input: LocationSearchInput, key: str) -> str:
        rewrite_result = tool_input.rewriteResult or {}
        if not isinstance(rewrite_result, dict):
            return ""
        extracted_slots = rewrite_result.get("extractedSlots") or {}
        if not isinstance(extracted_slots, dict):
            return ""
        value = extracted_slots.get(key)
        return value if isinstance(value, str) else ""

    def _extract_location_from_text(self, text: str) -> str:
        normalized = self._clean_text(text)
        if not normalized:
            return ""

        # 优先处理“某地附近”这种最常见表达。
        if "附近" in normalized:
            prefix = normalized.split("附近", 1)[0]
            prefix = self._strip_keyword_candidates(prefix)
            prefix = self._strip_common_prefix(prefix)
            return self._clean_text(prefix)

        working = normalized
        for marker in self._LOCATION_END_MARKERS:
            if marker in working:
                working = working.split(marker, 1)[0]
                break
        working = self._strip_keyword_candidates(working)
        working = self._strip_common_prefix(working)
        return self._clean_text(working)

    def _extract_keyword_from_text(self, text: str) -> str:
        normalized = self._clean_text(text)
        for candidate in self._KEYWORD_CANDIDATES:
            if candidate in normalized:
                return candidate
        return ""

    def _strip_keyword_candidates(self, text: str) -> str:
        cleaned = text
        for candidate in self._KEYWORD_CANDIDATES:
            cleaned = cleaned.replace(candidate, "")
        return cleaned

    def _strip_common_prefix(self, text: str) -> str:
        prefixes = ("请问", "帮我找", "帮我查", "我想找", "想找", "查一下", "搜一下")
        cleaned = text
        for prefix in prefixes:
            if cleaned.startswith(prefix):
                cleaned = cleaned[len(prefix) :]
        return cleaned

    def _clean_text(self, value: str | None) -> str:
        if not value:
            return ""
        return str(value).strip().strip("，。！？,.!?：: ")

    def _normalize_poi(self, poi: dict[str, Any]) -> LocationSearchResultItem:
        """把高德 POI 结果转成统一结构。

        高德部分字段在不同 POI 下可能缺失，因此这里统一做字符串兜底，
        避免第二轮 Prompt 因字段不存在而出现不稳定行为。
        """

        business = poi.get("business") if isinstance(poi.get("business"), dict) else {}
        tel = self._clean_text(poi.get("tel")) or self._clean_text(business.get("tel"))
        region_parts = [self._clean_text(poi.get("pname")), self._clean_text(poi.get("cityname")), self._clean_text(poi.get("adname"))]
        region = "".join(part for part in region_parts if part)
        return LocationSearchResultItem(
            name=self._clean_text(poi.get("name")),
            address=self._clean_text(poi.get("address")),
            region=region,
            location=self._clean_text(poi.get("location")),
            tel=tel,
            type=self._clean_text(poi.get("type")),
        )
