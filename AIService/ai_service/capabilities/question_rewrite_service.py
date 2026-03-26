"""Question Rewrite 前置服务。

这层位于 Tool Router 之前，只负责把问题“看明白”：
- 规则优先识别高确定性场景
- 必要时调用 LLM 做标准化补充
- 输出结构化结果供后续 DecisionService 使用

它不直接回答用户，也不直接执行工具。
"""

from __future__ import annotations

import json

from ai_service.capabilities.question_rewrite_rules import QuestionRewriteRules
from ai_service.core.logging import get_logger
from ai_service.core.settings import Settings
from ai_service.observability.log_service import LogService
from ai_service.prompts.prompt_builder import PromptBuilder
from ai_service.providers.llm.qwen_provider import QwenProvider
from ai_service.schemas.chat_request import ChatRequest
from ai_service.schemas.question_rewrite import (
    QuestionRewriteResult,
    RewriteIntent,
    RewriteSource,
)

logger = get_logger(__name__)


class QuestionRewriteService:
    """规则 + LLM 的问题重写服务。"""

    def __init__(
        self,
        settings: Settings,
        prompt_builder: PromptBuilder,
        llm_provider: QwenProvider,
        log_service: LogService,
    ) -> None:
        self._settings = settings
        self._prompt_builder = prompt_builder
        self._llm_provider = llm_provider
        self._log_service = log_service
        self._rules = QuestionRewriteRules()

    def rewrite(
        self,
        request: ChatRequest,
        context_messages: list[dict],
    ) -> tuple[QuestionRewriteResult, dict | None]:
        """返回最终的结构化重写结果及可选的 LLM 原始输出。"""
        rule_result = self._rules.rewrite(request=request, context_messages=context_messages)

        # 规则高置信命中时直接返回，避免额外模型开销。
        if rule_result is not None and rule_result.confidence >= 0.9:
            return rule_result, None

        llm_result = self._rewrite_with_llm(request=request, context_messages=context_messages)
        if llm_result is None:
            if rule_result is not None:
                return rule_result, None
            return self._fallback_result(request), None

        if rule_result is None:
            return llm_result, {"source": "llm"}

        merged = self._merge_results(rule_result=rule_result, llm_result=llm_result)
        return merged, {"source": "hybrid"}

    def _rewrite_with_llm(
        self,
        request: ChatRequest,
        context_messages: list[dict],
    ) -> QuestionRewriteResult | None:
        messages = self._prompt_builder.build_question_rewrite_messages(
            request=request,
            context_messages=context_messages,
        )
        try:
            llm_result = self._llm_provider.chat(messages)
            self._log_service.log_llm_round(
                request_id=request.requestId,
                stage="question_rewrite",
                messages=messages,
                llm_result=llm_result,
            )
        except Exception as exc:
            self._log_service.log_llm_error(
                request_id=request.requestId,
                stage="question_rewrite",
                messages=messages,
                error=str(exc),
            )
            logger.warning("question rewrite llm failed, requestId=%s, error=%s", request.requestId, exc)
            return None

        content = llm_result.get("content", "")
        try:
            payload = json.loads(content)
            rewrite_result = QuestionRewriteResult.model_validate(payload)
            rewrite_result.source = RewriteSource.LLM
            return rewrite_result
        except Exception:
            logger.warning("question rewrite llm output is not valid json, requestId=%s", request.requestId)
            return None

    def _merge_results(
        self,
        rule_result: QuestionRewriteResult,
        llm_result: QuestionRewriteResult,
    ) -> QuestionRewriteResult:
        """规则层与 LLM 层合并。

        合并原则：
        - 原问题保留原始 query
        - followUp 和 suggestedToolName 优先尊重高确定性的规则层
        - 标准化描述优先采用 LLM 的自然化结果
        - 来源标记为 hybrid
        """
        merged_confidence = max(rule_result.confidence, llm_result.confidence)
        merged_tags = list(dict.fromkeys(rule_result.reasoningTags + llm_result.reasoningTags))
        extracted_slots = dict(rule_result.extractedSlots)
        extracted_slots.update(llm_result.extractedSlots)

        return QuestionRewriteResult(
            originalQuestion=rule_result.originalQuestion,
            normalizedQuestion=llm_result.normalizedQuestion or rule_result.normalizedQuestion,
            intentType=rule_result.intentType
            if rule_result.intentType != RewriteIntent.UNKNOWN
            else llm_result.intentType,
            suggestTool=rule_result.suggestTool or llm_result.suggestTool,
            suggestedToolName=rule_result.suggestedToolName or llm_result.suggestedToolName,
            followUp=rule_result.followUp or llm_result.followUp,
            needKnowledgeRetrieval=rule_result.needKnowledgeRetrieval or llm_result.needKnowledgeRetrieval,
            confidence=min(1.0, merged_confidence),
            source=RewriteSource.HYBRID,
            reasoningTags=merged_tags,
            extractedSlots=extracted_slots,
        )

    def _fallback_result(self, request: ChatRequest) -> QuestionRewriteResult:
        query = (request.message or "").strip()
        return QuestionRewriteResult(
            originalQuestion=query,
            normalizedQuestion=query,
            intentType=RewriteIntent.UNKNOWN,
            suggestTool=False,
            suggestedToolName=None,
            followUp=False,
            needKnowledgeRetrieval=False,
            confidence=0.2,
            source=RewriteSource.FALLBACK,
            reasoningTags=["fallback"],
            extractedSlots={"petId": request.pet.petId if request.pet is not None else None},
        )
