"""记忆存储抽象接口。"""

from abc import ABC, abstractmethod


class MemoryProvider(ABC):
    """统一的短期记忆接口。"""

    @abstractmethod
    async def connect(self) -> None:
        """建立底层连接。"""

    @abstractmethod
    async def close(self) -> None:
        """关闭底层连接。"""

    @abstractmethod
    async def load_messages(self, conversation_id: str) -> list[dict]:
        """读取会话消息。"""

    @abstractmethod
    async def save_messages(self, conversation_id: str, messages: list[dict], ttl_seconds: int) -> None:
        """保存会话消息。"""
