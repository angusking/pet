"""体重分析上下文整理器。

这一层只把后端原始记录整理成适合喂给 LLM 的稳定结构，
不直接输出最终分析结论。
"""

from __future__ import annotations

from typing import Any

from ai_service.tools.weight_analysis.schemas import WeightAnalysisContext, WeightRecordItem


class WeightAnalysisContextBuilder:
    """构建体重分析上下文。"""

    def build(self, payload: dict[str, Any]) -> WeightAnalysisContext:
        raw_records = payload.get("records", []) or []
        normalized_records: list[WeightRecordItem] = []
        previous_value: float | None = None

        # 后端返回默认按 recordedAt 倒序排列，这里直接保持“最近在前”的顺序。
        for record in raw_records:
            current_value = self._safe_float(record.get("weightValue"))
            delta = None
            if current_value is not None and previous_value is not None:
                delta = round(current_value - previous_value, 2)
            normalized_records.append(
                WeightRecordItem(
                    recordedAt=str(record.get("recordedAt") or ""),
                    weightValue=current_value or 0.0,
                    unit=str(record.get("unit") or "kg"),
                    source=record.get("source"),
                    note=record.get("note"),
                    deltaFromPrevious=delta,
                )
            )
            previous_value = current_value

        return WeightAnalysisContext(
            petId=int(payload.get("petId", 0)),
            petName=str(payload.get("petName") or ""),
            categoryPath=payload.get("categoryPath"),
            displaySpecies=payload.get("displaySpecies"),
            birthDate=str(payload.get("birthDate")) if payload.get("birthDate") is not None else None,
            gender=payload.get("gender"),
            neutered=payload.get("neutered"),
            currentWeight=self._safe_float(payload.get("currentWeight")),
            latestRecordedAt=normalized_records[0].recordedAt if normalized_records else None,
            recordCount=int(payload.get("recordCount") or len(normalized_records)),
            insufficientData=len(normalized_records) < 2,
            records=normalized_records,
        )

    def _safe_float(self, value: Any) -> float | None:
        if value is None:
            return None
        try:
            return float(value)
        except (TypeError, ValueError):
            return None
