"""用于 Redis miss 时回源 backend 历史消息的 Provider。"""

from __future__ import annotations

import httpx


class BackendHistoryProvider:
    """通过 backend 内部接口拉取最近对话消息。"""

    def __init__(self, base_url: str, timeout_seconds: int) -> None:
        self._base_url = base_url.rstrip("/")
        self._timeout_seconds = timeout_seconds

    async def load_recent_messages(
        self,
        conversation_id: str,
        user_id: int,
        limit: int,
    ) -> list[dict]:
        """按会话 ID 拉取最近消息。

        当前阶段默认认为 `conversation_id` 就是 backend 的 `sessionId`。
        如果它不是纯数字，说明暂时无法映射到 backend 会话，这里直接降级为空列表。
        """
        if not conversation_id.isdigit():
            return []

        url = f"{self._base_url}/internal/ai/chats/{conversation_id}/recent-messages"
        async with httpx.AsyncClient(timeout=self._timeout_seconds) as client:
            response = await client.get(
                url,
                params={"userId": user_id, "limit": limit},
            )
            response.raise_for_status()
            payload = response.json()

        # backend 使用统一的 ApiResponse 包装，这里只抽取真正的 data 内容。
        items = payload.get("data") if isinstance(payload, dict) else payload
        if not isinstance(items, list):
            return []

        normalized: list[dict] = []
        for item in items:
            if not isinstance(item, dict):
                continue
            role = str(item.get("role", "")).strip()
            content = str(item.get("content", "")).strip()
            if role and content:
                normalized.append({"role": role, "content": content})
        return normalized
