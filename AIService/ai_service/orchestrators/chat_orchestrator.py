"""聊天编排入口。"""

import json
from time import perf_counter

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
        self._rewrite_service = RewriteService()
        self._rag_service = RagService()
        self._tool_service = ToolService()
        self._safety_service = SafetyService()
        self._log_service = LogService(settings.log_dir)

    async def handle(self, request: ChatRequest) -> ChatResponse:
        """执行完整的聊天处理流程。"""
        started = perf_counter()
        used_rewrite = False
        used_rag = False
        used_tool = False
        model_name = self._settings.qwen_model
        memory_source = "empty"

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

            tool_result = await self._tool_service.invoke_if_needed(
                query=rewritten_query,
                biz_data=request.bizData.model_dump() if request.bizData else None,
            )
            used_tool = bool(tool_result)

            messages = self._prompt_builder.build_messages(
                request=request,
                context_messages=context_messages,
                rewritten_query=rewritten_query,
                rag_context=rag_context,
                tool_result=tool_result,
            )

            llm_result = self._llm_provider.chat(messages)
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
                    **llm_result.get("usage", {}),
                    "memorySource": memory_source,
                    "contextMessageCount": len(context_messages),
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
                answer="抱歉，AI 助手暂时无法处理您的请求，请稍后再试。",
                riskLevel=RiskLevel.LOW,
                checklist=[],
                services=[],
                followUps=[],
            )

    def _parse_llm_output(self, request_id: str, content: str) -> ChatResponse:
        """把模型输出解析成稳定的响应结构。"""
        try:
            data = json.loads(content)
            return ChatResponse(
                requestId=request_id,
                answer=data.get("answer", ""),
                riskLevel=data.get("riskLevel", "low"),
                checklist=data.get("checklist", []),
                services=data.get("services", []),
                followUps=data.get("followUps", []),
                disclaimer=data.get(
                    "disclaimer",
                    "本回答仅供宠物日常护理参考，不能替代执业兽医诊断。",
                ),
            )
        except Exception:
            logger.warning("llm output is not valid json, fallback to plain answer")
            return ChatResponse(
                requestId=request_id,
                answer=content or "未获取到有效回答。",
                riskLevel=RiskLevel.LOW,
                checklist=[],
                services=[],
                followUps=[],
            )
