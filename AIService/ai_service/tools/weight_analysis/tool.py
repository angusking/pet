"""体重分析 Tool 对外入口。"""

from typing import Any

from ai_service.core.settings import Settings
from ai_service.providers.backend.pet_weight_provider import PetWeightProvider
from ai_service.tools.base import BaseTool
from ai_service.tools.weight_analysis.analyzer import WeightAnalyzer
from ai_service.tools.weight_analysis.schemas import WeightAnalysisInput


class WeightAnalysisTool(BaseTool):
    """调用 Java 后端并分析体重趋势的 Tool。"""

    name = "weight_analysis"

    def __init__(self, settings: Settings) -> None:
        self._provider = PetWeightProvider(settings=settings)
        self._analyzer = WeightAnalyzer()
        self._limit = settings.weight_analysis_limit

    async def run(self, payload: dict[str, Any]) -> dict[str, Any]:
        """执行体重分析流程。"""
        tool_input = WeightAnalysisInput.model_validate(payload)
        backend_payload = await self._provider.fetch_weight_records(
            user_id=tool_input.userId,
            pet_id=tool_input.petId,
            limit=self._limit,
        )
        return self._analyzer.analyze(backend_payload).model_dump()
