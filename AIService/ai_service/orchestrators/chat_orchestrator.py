"""聊天编排器。

这是整个 AIService 的核心模块，负责把一次聊天请求串成完整流程。
它不关心 HTTP 协议细节，而只负责把输入安全、稳定地转换成结构化输出。
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

        try:
            # 1. 读取短期记忆，作为多轮对话上下文的补充。
            memory_messages = await self._memory_service.load_memory(request.conversationId)

            # 2. 问题重写。
            # V1 中采用最保守策略，大部分情况下直接返回原问题，
            # 但仍然保留独立模块，方便后续升级。
            rewritten_query = self._rewrite_service.rewrite(request.message)
            used_rewrite = rewritten_query != request.message

            # 3. RAG 检索。
            # V1 暂不接知识库，这里保留标准扩展位。
            rag_context = await self._rag_service.retrieve(rewritten_query)
            used_rag = bool(rag_context)

            # 4. 工具调用。
            # 当前只实现了一个轻量级的体重分析工具作为示例。
            tool_result = await self._tool_service.invoke_if_needed(
                query=rewritten_query,
                biz_data=request.bizData.model_dump() if request.bizData else None,
            )
            used_tool = bool(tool_result)

            # 5. 构建 Prompt。
            messages = self._prompt_builder.build_messages(
                request=request,
                memory_messages=memory_messages,
                rewritten_query=rewritten_query,
                rag_context=rag_context,
                tool_result=tool_result,
            )

            # 6. 调用大模型。
            llm_result = self._llm_provider.chat(messages)
            model_name = llm_result.get("model", model_name)

            # 7. 解析模型输出，统一收敛成 ChatResponse。
            response = self._parse_llm_output(
                request_id=request.requestId,
                content=llm_result.get("content", ""),
            )

            # 8. 安全校验。
            response = self._safety_service.enforce(response=response, original_query=request.message)

            # 9. 保存本轮会话记忆。
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
                usage=llm_result.get("usage", {}),
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
                error=str(exc),
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
        """把大模型文本输出解析成标准响应。"""
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
            # 如果模型没有按要求输出 JSON，这里走兜底逻辑，
            # 这样上游系统仍然能拿到一个可消费的结构化响应。
            logger.warning("llm output is not valid json, fallback to plain answer")
            return ChatResponse(
                requestId=request_id,
                answer=content or "未获取到有效回答。",
                riskLevel=RiskLevel.LOW,
                checklist=[],
                services=[],
                followUps=[],
            )
