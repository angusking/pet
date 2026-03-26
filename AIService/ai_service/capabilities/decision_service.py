"""第一轮决策服务。

职责非常单一：
1. 组织第一轮决策 Prompt
2. 调用模型
3. 把返回内容解析成稳定的 ToolDecision
"""

import json

from ai_service.core.logging import get_logger
from ai_service.prompts.prompt_builder import PromptBuilder
from ai_service.providers.llm.qwen_provider import QwenProvider
from ai_service.schemas.chat_request import ChatRequest
from ai_service.schemas.question_rewrite import QuestionRewriteResult
from ai_service.schemas.tool_decision import ToolDecision

logger = get_logger(__name__)


class DecisionService:
    """执行第一轮工具决策。"""

    def __init__(
        self,
        prompt_builder: PromptBuilder,
        llm_provider: QwenProvider,
        log_service,
    ) -> None:
        self._prompt_builder = prompt_builder
        self._llm_provider = llm_provider
        self._log_service = log_service

    def decide(
        self,
        request: ChatRequest,
        context_messages: list[dict],
        rewrite_result: QuestionRewriteResult,
        rag_context: str | None,
    ) -> tuple[ToolDecision, dict]:
        """调用第一轮决策模型，并返回解析结果及原始模型输出。"""
        messages = self._prompt_builder.build_decision_messages(
            request=request,
            context_messages=context_messages,
            rewrite_result=rewrite_result,
            rag_context=rag_context,
        )
        try:
            llm_result = self._llm_provider.chat(messages)
            self._log_service.log_llm_round(
                request_id=request.requestId,
                stage="decision",
                messages=messages,
                llm_result=llm_result,
            )
        except Exception as exc:
            self._log_service.log_llm_error(
                request_id=request.requestId,
                stage="decision",
                messages=messages,
                error=str(exc),
            )
            raise
        content = llm_result.get("content", "")
        try:
            payload = json.loads(content)
            decision = ToolDecision.model_validate(payload)
        except Exception:
            logger.warning("decision output is not valid json, fallback to direct answer")
            decision = ToolDecision(
                needTool=False,
                toolName=None,
                toolInput=None,
                followUp=rewrite_result.followUp,
                intent="UNKNOWN",
                answer=content or "暂时未获得有效结果，请稍后重试。",
            )
        return decision, llm_result
