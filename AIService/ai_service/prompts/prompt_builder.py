"""Prompt 构建器。

该模块把不同来源的上下文数据统一拼装成 LLM 所需的 messages，
从而确保 Prompt 逻辑不散落在多个业务文件中。
"""

from pathlib import Path

from ai_service.core.settings import Settings
from ai_service.schemas.chat_request import ChatRequest


class PromptBuilder:
    """负责拼接模型输入消息。"""

    def __init__(self, settings: Settings) -> None:
        self._settings = settings

    def build_messages(
        self,
        request: ChatRequest,
        memory_messages: list[dict],
        rewritten_query: str,
        rag_context: str | None,
        tool_result: dict | None,
    ) -> list[dict[str, str]]:
        """构建传递给大模型的消息列表。"""
        system_prompt = self._load_system_prompt()

        # 这里把结构化上下文压成一段文本，是为了兼容当前最通用的 chat 接口模式。
        # 后续如果要升级为更细粒度的多消息结构，也可以在这里统一调整。
        user_context_lines = [
            f"requestId: {request.requestId}",
            f"conversationId: {request.conversationId}",
            f"userId: {request.userId}",
            f"originalMessage: {request.message}",
            f"rewrittenMessage: {rewritten_query}",
        ]

        if request.pet is not None:
            user_context_lines.extend(
                [
                    f"pet.petId: {request.pet.petId}",
                    f"pet.name: {request.pet.name}",
                    f"pet.type: {request.pet.type}",
                    f"pet.age: {request.pet.age}",
                    f"pet.weight: {request.pet.weight}",
                ]
            )

        if request.bizData is not None:
            user_context_lines.append(f"bizData: {request.bizData.model_dump_json()}")

        if rag_context:
            user_context_lines.append(f"ragContext: {rag_context}")

        if tool_result:
            user_context_lines.append(f"toolResult: {tool_result}")

        messages: list[dict[str, str]] = [{"role": "system", "content": system_prompt}]
        messages.extend(memory_messages)
        messages.append({"role": "user", "content": "\n".join(user_context_lines)})
        return messages

    def _load_system_prompt(self) -> str:
        """从文件中读取系统提示词。"""
        path = Path(self._settings.system_prompt_file)
        if path.exists():
            return path.read_text(encoding="utf-8").strip()
        return "你是一位宠物健康与日常养护助手，请严格输出 JSON。"
