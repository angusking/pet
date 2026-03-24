"""体重分析核心逻辑。

这里使用规则分析而不是再次调用大模型，原因有两个：
1. 首阶段先把链路跑通，避免 Tool 内再套一层 LLM 增加复杂度。
2. 体重趋势判断本身比较结构化，规则实现更稳定、也更容易测试。
"""

from __future__ import annotations

from typing import Any

from ai_service.tools.weight_analysis.schemas import WeightAnalysisResult


class WeightAnalyzer:
    """根据最近体重记录生成趋势分析。"""

    def analyze(self, payload: dict[str, Any]) -> WeightAnalysisResult:
        """把后端返回的体重数据转换成结构化分析结果。"""
        records = payload.get("records", []) or []
        record_count = len(records)
        pet_id = int(payload.get("petId", 0))
        support_level = payload.get("supportLevel") or "trend_only"
        weight_hint = payload.get("categoryWeightHint") or ""
        current_weight = self._safe_float(payload.get("currentWeight"))
        previous_weight = self._safe_float(payload.get("previousWeight"))
        change_from_previous = self._safe_float(payload.get("changeFromPrevious"))

        if record_count < 2 or current_weight is None or previous_weight is None:
            return WeightAnalysisResult(
                status="no_data",
                petId=pet_id,
                supportLevel=support_level,
                categoryWeightHint=weight_hint,
                recordCount=record_count,
                currentWeight=current_weight,
                previousWeight=previous_weight,
                changeFromPrevious=change_from_previous,
                trend="unknown",
                analysis="当前体重记录不足，暂时无法判断连续趋势。",
                observations=["建议继续补充体重记录后再看变化。"],
                riskHint="体重趋势只适合结合连续记录观察，不能替代专业诊疗判断。",
            )

        values = [self._safe_float(record.get("weightValue")) for record in records]
        clean_values = [value for value in values if value is not None]
        trend = self._detect_trend(clean_values)
        observations = self._build_observations(
            trend=trend,
            support_level=support_level,
            weight_hint=weight_hint,
        )

        return WeightAnalysisResult(
            status="success",
            petId=pet_id,
            supportLevel=support_level,
            categoryWeightHint=weight_hint,
            recordCount=record_count,
            currentWeight=current_weight,
            previousWeight=previous_weight,
            changeFromPrevious=change_from_previous,
            trend=trend,
            analysis=self._build_analysis(
                current_weight=current_weight,
                previous_weight=previous_weight,
                change_from_previous=change_from_previous,
                trend=trend,
            ),
            observations=observations,
            riskHint=self._build_risk_hint(support_level=support_level),
        )

    def _detect_trend(self, values: list[float]) -> str:
        """按最近记录的整体变化方向做一个保守趋势判断。"""
        if len(values) < 2:
            return "unknown"

        first_value = values[-1]
        last_value = values[0]
        delta = last_value - first_value

        if abs(delta) < 0.05:
            return "stable"
        if delta > 0:
            return "up"
        return "down"

    def _build_analysis(
        self,
        current_weight: float,
        previous_weight: float,
        change_from_previous: float | None,
        trend: str,
    ) -> str:
        """生成简洁的一句话分析摘要。"""
        if change_from_previous is None:
            return f"当前最近一次体重为 {current_weight:.2f}kg，建议继续观察后续连续记录。"

        change_text = self._format_delta(change_from_previous)
        trend_text = {
            "up": "整体呈上升趋势",
            "down": "整体呈下降趋势",
            "stable": "整体较稳定",
            "unknown": "暂时无法明确判断整体趋势",
        }.get(trend, "暂时无法明确判断整体趋势")
        return (
            f"当前最近一次体重为 {current_weight:.2f}kg，"
            f"较前一次 {previous_weight:.2f}kg {change_text}，最近记录{trend_text}。"
        )

    def _build_observations(
        self,
        trend: str,
        support_level: str,
        weight_hint: str,
    ) -> list[str]:
        """生成补充观察点。"""
        observations = []
        if trend == "up":
            observations.append("最近记录以小幅上升为主，建议结合饮食和活动量继续观察。")
        elif trend == "down":
            observations.append("最近记录有下降趋势，建议结合食欲和精神状态一起看。")
        elif trend == "stable":
            observations.append("最近记录波动不大，当前体重相对稳定。")

        if support_level == "trend_only":
            observations.append("该类别更适合观察连续趋势，不建议只根据单次体重判断。")
        elif weight_hint:
            observations.append(weight_hint)
        return observations

    def _build_risk_hint(self, support_level: str) -> str:
        """生成统一的风险提示。"""
        if support_level == "trend_only":
            return "该类别个体差异较大，体重分析更适合做日常趋势观察，不替代兽医判断。"
        return "体重变化应结合年龄、绝育情况、食欲、精神状态和排便情况综合判断。"

    def _format_delta(self, delta: float) -> str:
        """把体重变化值转成更自然的中文描述。"""
        if abs(delta) < 0.05:
            return "变化不大"
        if delta > 0:
            return f"增加了 {delta:.2f}kg"
        return f"减少了 {abs(delta):.2f}kg"

    def _safe_float(self, value: Any) -> float | None:
        """兼容后端返回的数值类型，统一安全转浮点。"""
        if value is None:
            return None
        try:
            return float(value)
        except (TypeError, ValueError):
            return None
