"""体重分析 Tool 的 LLM 分析器。"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from ai_service.core.settings import Settings
from ai_service.observability.log_service import LogService
from ai_service.providers.llm.qwen_provider import QwenProvider
from ai_service.tools.weight_analysis.schemas import WeightAnalysisContext, WeightAnalysisResult


class WeightAnalysisLlmAnalyzer:
    """把整理后的体重记录交给 LLM 生成分析。"""

    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._llm_provider = QwenProvider(settings=settings)
        self._log_service = LogService(settings.log_dir)

    def analyze(
        self,
        request_id: str,
        user_message: str | None,
        context: WeightAnalysisContext,
    ) -> WeightAnalysisResult:
        messages = self._build_messages(user_message=user_message, context=context)
        llm_result = self._llm_provider.chat(messages)
        self._log_service.log_llm_round(
            request_id=request_id,
            stage="weight_analysis_tool",
            messages=messages,
            llm_result=llm_result,
        )
        payload = self._normalize_payload(
            request_id=request_id,
            content=llm_result.get("content", ""),
        )
        payload.setdefault("tool", "weight_analysis")
        payload.setdefault("petId", context.petId)
        payload.setdefault("recordCount", context.recordCount)
        payload.setdefault("currentWeight", context.currentWeight)
        payload.setdefault("latestRecordedAt", context.latestRecordedAt)
        return WeightAnalysisResult.model_validate(payload)

    def _build_messages(
        self,
        user_message: str | None,
        context: WeightAnalysisContext,
    ) -> list[dict[str, str]]:
        prompt = Path(self._settings.weight_analysis_tool_prompt_file).read_text(encoding="utf-8").strip()
        return [
            {"role": "system", "content": prompt},
            {
                "role": "user",
                "content": "\n".join(
                    [
                        f"userMessage: {user_message or ''}",
                        "fieldDescription.records: 体重记录数组，顺序为最近记录在前、较早记录在后。",
                        "fieldDescription.records.recordedAt: 该次体重被实际称量并记录的时间，用于判断新旧先后，不能当作创建时间或更新时间理解。",
                        "fieldDescription.records.weightValue: 该次称量得到的体重数值。",
                        "fieldDescription.records.unit: 体重单位，当前通常为 kg。",
                        "fieldDescription.records.source: 记录来源，例如家用称重、医院称重。",
                        "fieldDescription.records.note: 本次记录备注，例如饭前、洗澡后、刚换粮一周。",
                        "fieldDescription.records.deltaFromPrevious: 相邻记录差值提示，只能辅助理解，若与 recordedAt 的先后理解冲突，优先相信原始记录时间。",
                        f"weightAnalysisContext: {context.model_dump_json()}",
                    ]
                ),
            },
        ]

    def _normalize_payload(self, request_id: str, content: str) -> dict[str, Any]:
        data = json.loads(content)
        if not isinstance(data, dict):
            raise ValueError("weight analysis llm output root is not an object")

        answer = data.get("summary")
        nested = self._try_parse_nested_json(answer)
        if nested is not None:
            merged = dict(data)
            merged.update(nested)
            if "requestId" not in merged or not merged.get("requestId"):
                merged["requestId"] = request_id
            return merged
        return data

    def _try_parse_nested_json(self, value: Any) -> dict[str, Any] | None:
        if not isinstance(value, str):
            return None
        text = value.strip()
        if not text.startswith("{") or not text.endswith("}"):
            return None
        try:
            payload = json.loads(text)
        except Exception:
            return None
        return payload if isinstance(payload, dict) else None
