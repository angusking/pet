"""聊天编排器。

这是 AIService 的核心模块。
它负责把一次聊天请求串成完整流程，而不是只做单一能力。

当前流程大致是：
1. 取短期记忆
2. 重写问题
3. RAG 检索
4. 工具调用
5. 拼 Prompt
6. 调大模型
7. 解析结果
8. 安全校验
9. 写回短期记忆
10. 记录日志
"""

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
from ai_service.providers.memory.base import MemoryProvider
from ai_service.schemas.chat_request import ChatRequest
from ai_service.schemas.chat_response import ChatResponse, RiskLevel
from ai_service.services.memory_service import MemoryService

logger = get_logger(__name__)


class ChatOrchestrator:
    """负责一次 AI 聊天请求的完整业务编排。"""

    def __init__(self, settings: Settings, memory_provider: MemoryProvider) -> None:
        self._settings = settings
        self._memory_service = MemoryService(
            memory_provider=memory_provider,
            max_messages=settings.memory_max_messages,
            ttl_seconds=settings.memory_ttl_seconds,
        )
        self._prompt_builder = PromptBuilder(settings=settings)
        self._llm_provider = QwenProvider(settings=settings)
        self._rewrite_service = RewriteService()
        self._rag_service = RagService()
        self._tool_service = ToolService()
        self._safety_service = SafetyService()
        self._log_service = LogService(settings.log_dir)

    async def handle(self, request: ChatRequest) -> ChatResponse:
        """执行一次完整聊天流程。"""
        started = perf_counter()
        used_rewrite = False
        used_rag = False
        used_tool = False
        model_name = self._settings.qwen_model
        memory_source = "empty"

        try:
            # 第一步先决定“这轮对话到底用什么上下文”。
            # 第一阶段规则是：
            # Redis 优先，backend recentMessages 兜底。
            context_messages, memory_source = await self._memory_service.load_memory(
                request.conversationId,
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

            # 只有本轮真正成功产出了 assistant answer，才把这一轮写进 Redis。
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
        """把大模型文本输出解析成标准响应。

        这里做兜底是因为模型不一定总能严格按 JSON 输出。
        即使失败，也要尽量返回一个前端能消费的结构。
        """
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
