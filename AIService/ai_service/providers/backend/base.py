"""后端内部接口调用基础封装。

所有需要访问 Java 后端内部 AI 接口的 Provider 都从这里继承，
统一处理 baseUrl、超时和返回状态校验。
"""

from typing import Any

import httpx

from ai_service.core.exceptions import ToolInvocationError


class BaseBackendProvider:
    """内部后端 Provider 基类。"""

    def __init__(self, base_url: str, timeout_seconds: int) -> None:
        self._base_url = base_url.rstrip("/")
        self._timeout_seconds = timeout_seconds

    async def get_json(self, path: str, params: dict[str, Any] | None = None) -> dict[str, Any]:
        """发送 GET 请求并返回业务 JSON 数据。

        Java 后端当前统一走 `ApiResponse` 包装，典型结构是：
        {
          "code": 0,
          "message": "ok",
          "data": {...}
        }

        所以这里要主动把 `data` 解包出来，避免上层 Tool 误把包裹层当成真正业务对象。
        """
        url = f"{self._base_url}{path}"
        async with httpx.AsyncClient(timeout=self._timeout_seconds) as client:
            response = await client.get(url, params=params)

        if response.status_code >= 400:
            raise ToolInvocationError(
                f"后端内部接口调用失败: status={response.status_code}, path={path}"
            )

        try:
            payload = response.json()
        except ValueError as exc:
            raise ToolInvocationError(f"后端内部接口返回的不是有效 JSON: path={path}") from exc

        if isinstance(payload, dict):
            # 兼容 Java 后端统一响应包装。
            if "data" in payload and isinstance(payload.get("data"), dict):
                return payload["data"]
            return payload

        raise ToolInvocationError(f"后端内部接口返回结构不符合预期: path={path}")
