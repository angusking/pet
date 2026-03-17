"""工具调用能力。

V1 只实现轻量工具路由，不做复杂的 function calling。
当前示例工具是体重趋势分析。
"""

from ai_service.tools.weight_analysis import WeightAnalysisTool


class ToolService:
    """工具调用服务。"""

    def __init__(self) -> None:
        self._weight_tool = WeightAnalysisTool()

    async def invoke_if_needed(self, query: str, biz_data: dict | None) -> dict | None:
        """根据问题内容决定是否调用工具。"""
        lower_query = query.lower()
        if "体重" not in query and "weight" not in lower_query:
            return None

        history = []
        if biz_data:
            history = biz_data.get("weightHistory", []) or []

        return self._weight_tool.run({"weightHistory": history})
