"""短期记忆服务。

这一层专门负责“短期记忆的使用规则”，而不是直接操作 Redis：
- 优先读 Redis
- Redis miss 时先用请求里的 recentMessages
- 两者都没有时，再尝试回源 backend 内部接口
- 写回前统一裁剪窗口，避免上下文无限膨胀
"""

from ai_service.providers.memory.backend_history_provider import BackendHistoryProvider
from ai_service.providers.memory.base import MemoryProvider


class MemoryService:
    """管理多轮对话的短期记忆。"""

    def __init__(
        self,
        memory_provider: MemoryProvider,
        max_messages: int,
        ttl_seconds: int,
        backend_history_provider: BackendHistoryProvider | None = None,
    ) -> None:
        self._memory_provider = memory_provider
        self._max_messages = max_messages
        self._ttl_seconds = ttl_seconds
        self._backend_history_provider = backend_history_provider

    async def load_memory(
        self,
        conversation_id: str,
        user_id: int,
        fallback_messages: list[dict] | None = None,
    ) -> tuple[list[dict], str]:
        """读取可用于本轮 prompt 的上下文消息，并返回来源标记。

        当前优先级：
        1. Redis 短期记忆
        2. 请求中携带的 recentMessages
        3. backend 内部 recent-messages 接口
        4. 空上下文
        """
        redis_messages = self._clip_messages(await self._memory_provider.load_messages(conversation_id))
        if redis_messages:
            return redis_messages, "redis"

        normalized_fallback = self._clip_messages(fallback_messages or [])
        if normalized_fallback:
            await self._memory_provider.save_messages(
                conversation_id=conversation_id,
                messages=normalized_fallback,
                ttl_seconds=self._ttl_seconds,
            )
            return normalized_fallback, "fallback"

        if self._backend_history_provider is not None:
            backend_messages = self._clip_messages(
                await self._backend_history_provider.load_recent_messages(
                    conversation_id=conversation_id,
                    user_id=user_id,
                    limit=self._max_messages,
                )
            )
            if backend_messages:
                await self._memory_provider.save_messages(
                    conversation_id=conversation_id,
                    messages=backend_messages,
                    ttl_seconds=self._ttl_seconds,
                )
                return backend_messages, "backend"

        return [], "empty"

    async def save_memory(self, conversation_id: str, user_message: str, assistant_message: str) -> None:
        """把一轮成功对话按 user/assistant 成对写回短期记忆。"""
        current_messages = self._clip_messages(await self._memory_provider.load_messages(conversation_id))
        current_messages.extend(
            [
                {"role": "user", "content": user_message},
                {"role": "assistant", "content": assistant_message},
            ]
        )
        await self._memory_provider.save_messages(
            conversation_id=conversation_id,
            messages=self._clip_messages(current_messages),
            ttl_seconds=self._ttl_seconds,
        )

    async def clear_memory(self, conversation_id: str) -> None:
        """清理指定会话的 Redis 短期记忆。

        删除 AI 会话时，需要把 Redis 中的上下文窗口同步删除，
        避免数据库已删但 Redis 里还残留旧对话。
        """
        await self._memory_provider.delete_messages(conversation_id)

    def _clip_messages(self, messages: list[dict]) -> list[dict]:
        """清洗非法消息，并只保留最近 N 条。"""
        normalized = [
            {
                "role": str(message.get("role", "")).strip(),
                "content": str(message.get("content", "")).strip(),
            }
            for message in messages
            if isinstance(message, dict) and message.get("role") and message.get("content")
        ]
        return normalized[-self._max_messages :]
