"""聊天编排入口。

当前主链路已经升级为：
1. 先做 Question Rewrite 前置标准化
2. 再做第一轮 Tool 决策
3. 需要 Tool 时执行 Tool
4. 最后生成用户可见回答
"""

import json
from time import perf_counter
from typing import Any

from ai_service.capabilities.decision_service import DecisionService
from ai_service.capabilities.question_rewrite_service import QuestionRewriteService
from ai_service.capabilities.rag_service import RagService
from ai_service.capabilities.safety_service import SafetyService
from ai_service.capabilities.tool_service import ToolService
from ai_service.core.logging import get_logger
from ai_service.core.settings import Settings
from ai_service.observability.log_service import LogService
from ai_service.prompts.prompt_builder import PromptBuilder
from ai_service.providers.llm.qwen_provider import QwenProvider
from ai_service.providers.memory.backend_history_provider import BackendHistoryProvider
from ai_service.providers.memory.base import MemoryProvider
from ai_service.schemas.chat_request import ChatRequest
from ai_service.schemas.chat_response import ChatResponse, RiskLevel
from ai_service.services.memory_service import MemoryService

logger = get_logger(__name__)


class ChatOrchestrator:
    """负责一次 AI 聊天请求的完整编排。"""

    def __init__(self, settings: Settings, memory_provider: MemoryProvider) -> None:
        self._settings = settings
        self._memory_service = MemoryService(
            memory_provider=memory_provider,
            max_messages=settings.memory_max_messages,
            ttl_seconds=settings.memory_ttl_seconds,
            backend_history_provider=BackendHistoryProvider(
                base_url=settings.backend_base_url,
                timeout_seconds=settings.backend_timeout_seconds,
            ),
        )
        self._prompt_builder = PromptBuilder(settings=settings)
        self._llm_provider = QwenProvider(settings=settings)
        self._log_service = LogService(settings.log_dir)
        self._question_rewrite_service = QuestionRewriteService(
            settings=settings,
            prompt_builder=self._prompt_builder,
            llm_provider=self._llm_provider,
            log_service=self._log_service,
        )
        self._decision_service = DecisionService(
            prompt_builder=self._prompt_builder,
            llm_provider=self._llm_provider,
            log_service=self._log_service,
        )
        self._rag_service = RagService()
        self._tool_service = ToolService(settings=settings)
        self._safety_service = SafetyService()

    async def handle(self, request: ChatRequest) -> ChatResponse:
        """执行完整的聊天处理流程。"""
        started = perf_counter()
        used_rewrite = False
        used_rag = False
        used_tool = False
        model_name = self._settings.qwen_model
        memory_source = "empty"
        decision = None
        rewrite_result = None

        try:
            context_messages, memory_source = await self._memory_service.load_memory(
                request.conversationId,
                user_id=request.userId,
                fallback_messages=[message.model_dump() for message in request.recentMessages],
            )

            rewrite_result, rewrite_meta = self._question_rewrite_service.rewrite(
                request=request,
                context_messages=context_messages,
            )
            used_rewrite = rewrite_result.normalizedQuestion != request.message

            rag_context = None
            if rewrite_result.needKnowledgeRetrieval:
                rag_context = await self._rag_service.retrieve(rewrite_result.normalizedQuestion)
                used_rag = bool(rag_context)

            decision, decision_llm_result = self._decision_service.decide(
                request=request,
                context_messages=context_messages,
                rewrite_result=rewrite_result,
                rag_context=rag_context,
            )
            model_name = decision_llm_result.get("model", model_name)

            if not decision.needTool:
                response = ChatResponse(
                    requestId=request.requestId,
                    followUp=decision.followUp,
                    intent=decision.intent,
                    answer=decision.answer,
                    riskLevel=decision.riskLevel,
                    checklist=decision.checklist,
                    services=decision.services,
                    followUps=decision.followUps,
                    followUpQuestions=decision.followUpQuestions,
                    actionCards=decision.actionCards,
                    disclaimer=decision.disclaimer,
                )
            else:
                tool_input = dict(decision.toolInput or {})
                tool_input["requestId"] = request.requestId
                tool_input["userMessage"] = request.message
                # 把 Question Rewrite 结果透传给 Tool，避免 Tool 再重复解析原问题。
                # 对地点搜索这类强依赖结构化槽位的 Tool，这能明显减少误判。
                tool_input["rewriteResult"] = rewrite_result.model_dump(mode="json")
                tool_result = await self._tool_service.invoke(
                    tool_name=decision.toolName or "",
                    tool_input=tool_input,
                )
                used_tool = True

                messages = self._prompt_builder.build_final_messages(
                    request=request,
                    context_messages=context_messages,
                    rewrite_result=rewrite_result,
                    rag_context=rag_context,
                    tool_result=tool_result,
                )

                try:
                    llm_result = self._llm_provider.chat(messages)
                    self._log_service.log_llm_round(
                        request_id=request.requestId,
                        stage="final",
                        messages=messages,
                        llm_result=llm_result,
                    )
                except Exception as exc:
                    self._log_service.log_llm_error(
                        request_id=request.requestId,
                        stage="final",
                        messages=messages,
                        error=str(exc),
                    )
                    raise
                model_name = llm_result.get("model", model_name)
                response = self._parse_llm_output(
                    request_id=request.requestId,
                    content=llm_result.get("content", ""),
                )

            response = self._safety_service.enforce(response=response, original_query=request.message)

            await self._memory_service.save_memory(
                conversation_id=request.conversationId,
                user_message=request.message,
                assistant_message=response.answer,
            )

            latency_ms = int((perf_counter() - started) * 1000)
            self._log_service.log_success(
                request=request,
                response=response,
                model=model_name,
                latency_ms=latency_ms,
                usage={
                    "memorySource": memory_source,
                    "contextMessageCount": len(context_messages),
                    "rewriteIntent": rewrite_result.intentType.value if rewrite_result else "unknown",
                    "rewriteSource": rewrite_result.source.value if rewrite_result else "fallback",
                    "rewriteConfidence": rewrite_result.confidence if rewrite_result else 0.0,
                    "rewriteNeedKnowledgeRetrieval": rewrite_result.needKnowledgeRetrieval if rewrite_result else False,
                    "rewriteSuggestedTool": rewrite_result.suggestedToolName if rewrite_result else None,
                    "rewriteMeta": rewrite_meta,
                    "decisionNeedTool": decision.needTool if decision else False,
                    "decisionToolName": decision.toolName if decision else None,
                },
                used_rewrite=used_rewrite,
                used_rag=used_rag,
                used_tool=used_tool,
            )
            return response
        except Exception as exc:
            latency_ms = int((perf_counter() - started) * 1000)
            logger.exception("chat orchestration failed, requestId=%s", request.requestId)
            self._log_service.log_error(
                request=request,
                model=model_name,
                latency_ms=latency_ms,
                error=f"memorySource={memory_source}; error={exc}",
                used_rewrite=used_rewrite,
                used_rag=used_rag,
                used_tool=used_tool,
            )
            return ChatResponse(
                requestId=request.requestId,
                followUp=False,
                intent="UNKNOWN",
                answer="抱歉，AI 助手暂时无法处理您的请求，请稍后再试。",
                riskLevel=RiskLevel.LOW,
                checklist=[],
                services=[],
                followUps=[],
                followUpQuestions=[],
                actionCards=[],
            )

    def _parse_llm_output(self, request_id: str, content: str) -> ChatResponse:
        """把模型输出解析成稳定的响应结构。"""
        try:
            data = self._normalize_llm_payload(request_id, content)
            return ChatResponse(
                requestId=request_id,
                followUp=bool(data.get("followUp", False)),
                intent=data.get("intent", "UNKNOWN"),
                answer=data.get("answer", ""),
                riskLevel=data.get("riskLevel", "low"),
                checklist=data.get("checklist", []),
                services=data.get("services", []),
                followUps=data.get("followUps", []),
                followUpQuestions=data.get("followUpQuestions", []),
                actionCards=data.get("actionCards", []),
                disclaimer=data.get(
                    "disclaimer",
                    "本回答仅供宠物日常养护参考，不能替代执业兽医诊断。",
                ),
            )
        except Exception:
            logger.warning("llm output is not valid json, fallback to plain answer")
            return ChatResponse(
                requestId=request_id,
                followUp=False,
                intent="UNKNOWN",
                answer=content or "未获取到有效回答。",
                riskLevel=RiskLevel.LOW,
                checklist=[],
                services=[],
                followUps=[],
                followUpQuestions=[],
                actionCards=[],
            )

    def _normalize_llm_payload(self, request_id: str, content: str) -> dict[str, Any]:
        """对模型输出做两层兜底归一化。"""
        data = json.loads(content)
        if not isinstance(data, dict):
            raise ValueError("llm output root is not an object")

        nested = self._try_parse_nested_answer(data.get("answer"))
        if nested is None:
            return data

        merged = dict(data)
        merged.update(nested)
        if "requestId" not in merged or not merged.get("requestId"):
            merged["requestId"] = request_id
        return merged

    def _try_parse_nested_answer(self, answer: Any) -> dict[str, Any] | None:
        """如果 answer 自身是一段 JSON 文本，则尝试继续解析。"""
        if not isinstance(answer, str):
            return None
        text = answer.strip()
        if not text.startswith("{") or not text.endswith("}"):
            return None
        try:
            nested = json.loads(text)
        except Exception:
            return None
        return nested if isinstance(nested, dict) else None
