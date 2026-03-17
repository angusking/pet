"""短期记忆服务。

这个服务位于编排器与底层 Redis Provider 之间，
负责把原始存储接口包装成更符合聊天场景的读写方式。
"""

from ai_service.providers.memory.base import MemoryProvider


class MemoryService:
    """对话短期记忆服务。"""

    def __init__(self, memory_provider: MemoryProvider, max_messages: int, ttl_seconds: int) -> None:
        self._memory_provider = memory_provider
        self._max_messages = max_messages
        self._ttl_seconds = ttl_seconds

    async def load_memory(self, conversation_id: str) -> list[dict]:
        """读取指定会话的短期记忆。"""
        return await self._memory_provider.load_messages(conversation_id)

    async def save_memory(self, conversation_id: str, user_message: str, assistant_message: str) -> None:
        """保存一轮对话并自动裁剪记忆窗口。"""
        current_messages = await self._memory_provider.load_messages(conversation_id)
        current_messages.extend(
            [
                {"role": "user", "content": user_message},
                {"role": "assistant", "content": assistant_message},
            ]
        )
        clipped = current_messages[-self._max_messages :]
        await self._memory_provider.save_messages(
            conversation_id=conversation_id,
            messages=clipped,
            ttl_seconds=self._ttl_seconds,
        )
