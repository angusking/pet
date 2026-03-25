"""聊天编排入口。

这次重构后的核心变化：
1. 第一轮先做 Tool 决策
2. 不需要 Tool 时，直接返回第一轮结果
3. 需要 Tool 时，先调 Tool，再进入第二轮生成最终回答
"""

import json
from typing import Any
from time import perf_counter

from ai_service.capabilities.decision_service import DecisionService
from ai_service.capabilities.rag_service import RagService
from ai_service.capabilities.rewrite_service import RewriteService
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
        self._decision_service = DecisionService(
            prompt_builder=self._prompt_builder,
            llm_provider=self._llm_provider,
            log_service=self._log_service,
        )
        self._rewrite_service = RewriteService()
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

        try:
            # 上下文恢复顺序保持稳定：
            # Redis 优先，请求里的 recentMessages 第二，backend 内部接口第三。
            context_messages, memory_source = await self._memory_service.load_memory(
                request.conversationId,
                user_id=request.userId,
                fallback_messages=[message.model_dump() for message in request.recentMessages],
            )

            rewritten_query = self._rewrite_service.rewrite(request.message)
            used_rewrite = rewritten_query != request.message

            rag_context = await self._rag_service.retrieve(rewritten_query)
            used_rag = bool(rag_context)

            # 第一轮先做内部决策，不急着直接生成最终答案。
            decision, decision_llm_result = self._decision_service.decide(
                request=request,
                context_messages=context_messages,
                rewritten_query=rewritten_query,
                rag_context=rag_context,
            )
            model_name = decision_llm_result.get("model", model_name)

            if not decision.needTool:
                # 不需要 Tool 时，第一轮结果就是最终结果。
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
                # 需要 Tool 时，先执行 Tool，再进入第二轮最终回答。
                tool_input = dict(decision.toolInput or {})
                tool_input["requestId"] = request.requestId
                tool_input["userMessage"] = request.message
                tool_result = await self._tool_service.invoke(
                    tool_name=decision.toolName or "",
                    tool_input=tool_input,
                )
                used_tool = True

                messages = self._prompt_builder.build_final_messages(
                    request=request,
                    context_messages=context_messages,
                    rewritten_query=rewritten_query,
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

            # 只有拿到有效 assistant 回复后，才把这一轮写回短期记忆。
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
        """对模型输出做两层兜底归一化。

        当前线上观察到的异常格式主要有两种：
        1. 正常格式：顶层就是目标 JSON
        2. 嵌套格式：顶层 JSON 的 answer 字段里又塞了一整段 JSON 字符串

        这里统一把第 2 种拍平，避免后端和前端继续收到“JSON 套 JSON”的脏结构。
        """
        data = json.loads(content)
        if not isinstance(data, dict):
            raise ValueError("llm output root is not an object")

        nested = self._try_parse_nested_answer(data.get("answer"))
        if nested is None:
            return data

        # 外层字段优先保留 requestId；真正业务字段优先取内层。
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
