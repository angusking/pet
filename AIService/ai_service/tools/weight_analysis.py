"""体重分析工具。"""

from ai_service.tools.base import BaseTool


class WeightAnalysisTool(BaseTool):
    """分析宠物体重历史变化趋势。"""

    name = "weight_analysis"

    def run(self, payload: dict) -> dict:
        """根据体重历史输出简单趋势说明。"""
        history = payload.get("weightHistory", []) or []

        if len(history) < 2:
            return {
                "tool": self.name,
                "summary": "体重历史数据不足，暂时无法判断趋势。",
                "trend": "unknown",
            }

        first = history[0]
        last = history[-1]

        if last > first:
            trend = "up"
            summary = "最近体重整体呈上升趋势。"
        elif last < first:
            trend = "down"
            summary = "最近体重整体呈下降趋势。"
        else:
            trend = "stable"
            summary = "最近体重整体较稳定。"

        return {
            "tool": self.name,
            "summary": summary,
            "trend": trend,
            "first": first,
            "last": last,
        }
