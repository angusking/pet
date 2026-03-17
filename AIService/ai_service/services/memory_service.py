"""短期记忆服务。

这个 service 位于：
编排器 <-> 记忆服务 <-> Redis Provider

它的职责不是直接操作 Redis，而是把“聊天记忆规则”集中写在这里。
这样以后改窗口大小、兜底策略、去重策略时，只改这一层就够了。
"""

from ai_service.providers.memory.base import MemoryProvider


class MemoryService:
    """对话短期记忆服务。"""

    def __init__(self, memory_provider: MemoryProvider, max_messages: int, ttl_seconds: int) -> None:
        self._memory_provider = memory_provider
        self._max_messages = max_messages
        self._ttl_seconds = ttl_seconds

    async def load_memory(self, conversation_id: str, fallback_messages: list[dict] | None = None) -> tuple[list[dict], str]:
        """读取上下文消息。

        第一阶段策略：
        1. 优先使用 Redis 里的短期记忆
        2. Redis miss 时，使用 backend 传来的 recentMessages 兜底
        3. 如果用了兜底消息，则顺手回写到 Redis，方便下一轮直接命中

        返回值：
        - 第一个值：真正要参与 prompt 的上下文消息
        - 第二个值：上下文来源，便于日志排查
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

        return [], "empty"

    async def save_memory(self, conversation_id: str, user_message: str, assistant_message: str) -> None:
        """保存一轮成功对话。

        这里明确采用“成对写入”：
        - user message
        - assistant message

        这样可以避免只写进去半轮失败消息，污染后续上下文。
        """
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

    def _clip_messages(self, messages: list[dict]) -> list[dict]:
        """清洗并裁剪消息窗口。

        这里做两件事：
        - 丢掉结构不完整的消息
        - 只保留最近 N 条，防止上下文无限膨胀
        """
        normalized = [
            {
                "role": str(message.get("role", "")).strip(),
                "content": str(message.get("content", "")).strip(),
            }
            for message in messages
            if isinstance(message, dict) and message.get("role") and message.get("content")
        ]
        return normalized[-self._max_messages :]
