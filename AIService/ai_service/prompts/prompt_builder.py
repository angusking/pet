"""Prompt 构建器。

当前编排链路拆成三类 prompt：
1. Question Rewrite：只负责标准化与结构化理解
2. Decision：只负责判断是否需要 Tool
3. Final Response：在拿到 Tool 结果后生成最终回答
"""

from pathlib import Path
from typing import Any

from ai_service.core.settings import Settings
from ai_service.schemas.chat_request import ChatRequest
from ai_service.schemas.question_rewrite import QuestionRewriteResult
from ai_service.tools.registry import ToolRegistry


class PromptBuilder:
    """统一组织 Rewrite、Decision 和 Final Response 的模型输入。"""

    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._tool_registry = ToolRegistry(settings=settings)

    def build_question_rewrite_messages(
        self,
        request: ChatRequest,
        context_messages: list[dict],
    ) -> list[dict[str, str]]:
        """构建 Question Rewrite 前置模块的 messages。"""
        messages = [
            {"role": "system", "content": self._load_prompt(self._settings.base_system_prompt_file)},
            {"role": "system", "content": self._load_prompt(self._settings.question_rewrite_prompt_file)},
        ]
        messages.extend(context_messages)
        messages.append(
            {
                "role": "user",
                "content": self._build_question_rewrite_context_text(request=request),
            }
        )
        return messages

    def build_decision_messages(
        self,
        request: ChatRequest,
        context_messages: list[dict],
        rewrite_result: QuestionRewriteResult,
        rag_context: str | None,
    ) -> list[dict[str, str]]:
        """构建第一轮“是否调用 Tool”的 messages。"""
        messages = [
            {"role": "system", "content": self._load_prompt(self._settings.base_system_prompt_file)},
            {"role": "system", "content": self._build_tool_registry_prompt()},
            {"role": "system", "content": self._load_prompt(self._settings.decision_prompt_file)},
        ]
        messages.extend(context_messages)
        messages.append(
            {
                "role": "user",
                "content": self._build_context_text(
                    request=request,
                    rewrite_result=rewrite_result,
                    rag_context=rag_context,
                    tool_result=None,
                ),
            }
        )
        return messages

    def build_final_messages(
        self,
        request: ChatRequest,
        context_messages: list[dict],
        rewrite_result: QuestionRewriteResult,
        rag_context: str | None,
        tool_result: dict[str, Any] | None,
    ) -> list[dict[str, str]]:
        """构建第二轮最终回答的 messages。"""
        messages = [
            {"role": "system", "content": self._load_prompt(self._settings.base_system_prompt_file)},
            {"role": "system", "content": self._load_prompt(self._settings.final_response_prompt_file)},
        ]
        messages.extend(context_messages)
        messages.append(
            {
                "role": "user",
                "content": self._build_context_text(
                    request=request,
                    rewrite_result=rewrite_result,
                    rag_context=rag_context,
                    tool_result=tool_result,
                ),
            }
        )
        return messages

    def _build_question_rewrite_context_text(self, request: ChatRequest) -> str:
        """构建 Question Rewrite 阶段需要的最小上下文。"""
        lines = [
            f"requestId: {request.requestId}",
            f"conversationId: {request.conversationId}",
            f"userId: {request.userId}",
            f"originalMessage: {request.message}",
        ]

        if request.pet is not None:
            lines.extend(
                [
                    f"pet.petId: {request.pet.petId}",
                    f"pet.name: {request.pet.name}",
                    f"pet.type: {request.pet.type}",
                    f"pet.age: {request.pet.age}",
                    f"pet.weight: {request.pet.weight}",
                ]
            )

        return "\n".join(lines)

    def _build_context_text(
        self,
        request: ChatRequest,
        rewrite_result: QuestionRewriteResult,
        rag_context: str | None,
        tool_result: dict[str, Any] | None,
    ) -> str:
        """把请求上下文统一压缩成一段稳定文本。"""
        lines = [
            f"requestId: {request.requestId}",
            f"conversationId: {request.conversationId}",
            f"userId: {request.userId}",
            f"originalMessage: {request.message}",
            f"rewriteResult: {rewrite_result.model_dump_json()}",
            f"rewrittenMessage: {rewrite_result.normalizedQuestion}",
        ]

        if request.pet is not None:
            lines.extend(
                [
                    f"pet.petId: {request.pet.petId}",
                    f"pet.name: {request.pet.name}",
                    f"pet.type: {request.pet.type}",
                    f"pet.age: {request.pet.age}",
                    f"pet.weight: {request.pet.weight}",
                ]
            )

        if request.bizData is not None:
            lines.append(f"bizData: {request.bizData.model_dump_json()}")

        if rag_context:
            lines.append(f"ragContext: {rag_context}")

        if tool_result is not None:
            lines.append(f"toolResult: {tool_result}")

        return "\n".join(lines)

    def _load_prompt(self, path_text: str) -> str:
        """从文件中读取 Prompt 文本。"""
        path = Path(path_text)
        if path.exists():
            return path.read_text(encoding="utf-8").strip()
        return "你是一位宠物健康与日常养护助手，请严格输出 JSON。"

    def _build_tool_registry_prompt(self) -> str:
        """把静态说明和当前注册表内容合并成第一轮可用的 Tool 提示词。"""
        base_text = self._load_prompt(self._settings.tool_registry_prompt_file)
        registry_text = self._tool_registry.build_registry_prompt_text()
        if not registry_text:
            return base_text
        return f"{base_text}\n\n当前实际启用的 Tool 列表如下：\n{registry_text}"
