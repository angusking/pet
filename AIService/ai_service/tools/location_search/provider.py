"""高德地点搜索 Provider。

首版直接对接高德 Web Service 文本搜索接口：
https://lbs.amap.com/api/webservice/guide/api-advanced/search

当前只接“文本搜索”这一条能力，原因是：
1. 现有前端和 Java 后端还没有稳定传经纬度；
2. 绝大多数用户表达是“浦东附近宠物医院”“北京朝阳宠物店”这类文本区域查询；
3. 先把文本搜索打通，后续再扩展周边搜索会更稳。
"""

from __future__ import annotations

from typing import Any

import httpx

from ai_service.core.exceptions import ToolInvocationError
from ai_service.core.logging import get_logger
from ai_service.core.settings import Settings

logger = get_logger(__name__)


class AmapPlaceSearchProvider:
    """封装高德 Web Service 文本搜索调用。"""

    def __init__(self, settings: Settings) -> None:
        self._key = settings.amap_web_service_key.strip()
        self._base_url = settings.amap_base_url.rstrip("/")
        self._timeout_seconds = settings.backend_timeout_seconds
        self._page_size = settings.amap_search_page_size

    async def search_text(self, *, keyword: str, region: str) -> dict[str, Any]:
        """按关键词和区域执行文本搜索。

        这里刻意只暴露业务层真正关心的两个参数：
        - `keyword`：要找什么
        - `region`：在哪找

        其它第三方细节参数在 Provider 内统一控制，避免向 Tool 层泄漏。
        """

        if not self._key:
            raise ToolInvocationError("AMAP_WEB_SERVICE_KEY 未配置，无法执行地点搜索。")

        params = {
            "key": self._key,
            "keywords": keyword,
            "region": region,
            "city_limit": "true",
            "page_size": self._page_size,
            "page_num": 1,
            # business 可以补回电话、营业类信息。即使部分 POI 没有这些字段，也不会影响结果解析。
            "show_fields": "business",
        }
        url = f"{self._base_url}/v5/place/text"

        logger.info("call amap text search, region=%s, keyword=%s, pageSize=%s", region, keyword, self._page_size)

        async with httpx.AsyncClient(timeout=self._timeout_seconds) as client:
            response = await client.get(url, params=params)

        if response.status_code >= 400:
            raise ToolInvocationError(
                f"高德地点搜索调用失败: status={response.status_code}, region={region}, keyword={keyword}"
            )

        try:
            payload = response.json()
        except ValueError as exc:
            raise ToolInvocationError("高德地点搜索返回的不是有效 JSON。") from exc

        # 高德返回状态约定：status=1 代表成功；info 是文字说明。
        if str(payload.get("status", "")) != "1":
            raise ToolInvocationError(
                f"高德地点搜索返回失败: info={payload.get('info', '')}, infocode={payload.get('infocode', '')}"
            )

        logger.info(
            "amap text search success, region=%s, keyword=%s, count=%s",
            region,
            keyword,
            payload.get("count", "0"),
        )
        return payload

