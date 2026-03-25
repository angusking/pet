"""Redis 短期记忆实现。

这一层只做“存取删”，不做业务规则判断。
例如：
- 不决定优先用 Redis 还是 fallback
- 不决定窗口大小
- 不决定失败时要不要写入

这些规则全部放在 MemoryService。
"""

import json

from redis.asyncio import Redis

from ai_service.core.settings import Settings
from ai_service.providers.memory.base import MemoryProvider


class RedisMemoryProvider(MemoryProvider):
    """基于 Redis 的短期记忆 Provider。"""

    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._redis: Redis | None = None

    async def connect(self) -> None:
        """初始化 Redis 连接。"""
        self._redis = Redis(
            host=self._settings.redis_host,
            port=self._settings.redis_port,
            db=self._settings.redis_db,
            decode_responses=True,
        )
        await self._redis.ping()

    async def close(self) -> None:
        """关闭 Redis 连接。"""
        if self._redis is not None:
            await self._redis.close()

    async def load_messages(self, conversation_id: str) -> list[dict]:
        """读取指定会话的全部短期记忆消息。"""
        assert self._redis is not None
        key = self._build_key(conversation_id)
        raw_messages = await self._redis.lrange(key, 0, -1)
        return [json.loads(item) for item in raw_messages]

    async def save_messages(self, conversation_id: str, messages: list[dict], ttl_seconds: int) -> None:
        """保存裁剪后的消息列表。

        这里采用“整段覆盖”而不是增量 append。
        好处是逻辑更直接：
        - Redis 里的内容永远就是当前最终窗口
        - 不需要再额外 trim
        - 出问题时更容易排查
        """
        assert self._redis is not None
        key = self._build_key(conversation_id)
        payload = [json.dumps(item, ensure_ascii=False) for item in messages]
        async with self._redis.pipeline(transaction=True) as pipe:
            await pipe.delete(key)
            if payload:
                await pipe.rpush(key, *payload)
            # 每次写入都刷新 TTL，表示“这个会话最近仍然活跃”。
            await pipe.expire(key, ttl_seconds)
            await pipe.execute()

    async def delete_messages(self, conversation_id: str) -> None:
        """直接删除整个会话的 Redis 短期记忆 key。"""
        assert self._redis is not None
        await self._redis.delete(self._build_key(conversation_id))

    def _build_key(self, conversation_id: str) -> str:
        """统一生成 Redis key。"""
        return f"ai:memory:{conversation_id}"
