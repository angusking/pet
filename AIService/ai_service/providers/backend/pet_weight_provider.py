"""宠物体重后端 Provider。

这里只做一件事：从 Java 后端内部接口拉取体重分析所需原始数据。
具体的趋势判断和文案生成，由 Tool 内部 analyzer 负责。
"""

from typing import Any

from ai_service.core.settings import Settings
from ai_service.providers.backend.base import BaseBackendProvider


class PetWeightProvider(BaseBackendProvider):
    """负责读取宠物体重记录内部接口。"""

    def __init__(self, settings: Settings) -> None:
        super().__init__(
            base_url=settings.backend_base_url,
            timeout_seconds=settings.backend_timeout_seconds,
        )

    async def fetch_weight_records(
        self,
        user_id: int,
        pet_id: int,
        limit: int,
    ) -> dict[str, Any]:
        """获取指定宠物的最近体重记录。"""
        return await self.get_json(
            path=f"/internal/ai/pets/{pet_id}/weight-records",
            params={"userId": user_id, "limit": limit},
        )
