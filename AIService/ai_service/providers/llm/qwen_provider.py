"""Qwen 调用封装。

这个类专门负责和 DashScope Python SDK 打交道。
它不关心：
- Redis 记忆
- Prompt 编排策略
- 安全校验
- 前端返回结构

它只做一件事：把 messages 发给模型，然后把结果转成统一结构。
"""

import json

from dashscope import Generation

from ai_service.core.exceptions import LLMInvocationError
from ai_service.core.settings import Settings
from ai_service.providers.llm.base import BaseLLMProvider


class QwenProvider(BaseLLMProvider):
    """Qwen 模型提供者。"""

    def __init__(self, settings: Settings) -> None:
        self._settings = settings

    def chat(self, messages: list[dict[str, str]]) -> dict:
        """调用 Qwen 并返回统一结构。

        返回字段：
        - content: 模型输出文本
        - model: 模型名称
        - usage: token 统计信息
        """
        if not self._settings.dashscope_api_key:
            # 这里抛“未配置”而不是“调用失败”，是为了排查时能马上区分出问题类型。
            raise LLMInvocationError("DASHSCOPE_API_KEY 未配置，无法调用 Qwen。")

        call_kwargs = {
            "model": self._settings.qwen_model,
            "api_key": self._settings.dashscope_api_key,
            "messages": messages,
            "result_format": "message",
        }

        # 某些环境需要自定义 DashScope 地址，例如国际站或代理网关。
        if self._settings.dashscope_base_url.strip():
            call_kwargs["base_address"] = self._settings.dashscope_base_url.strip()

        response = Generation.call(**call_kwargs)

        if response.status_code != 200:
            # 把底层 SDK 的 message 往上抛，方便判断是 key、额度、模型还是网络问题。
            raise LLMInvocationError(f"Qwen 调用失败: {response.message}")

        output = response.output.choices[0].message.content
        if not isinstance(output, str):
            # 某些模型/模式下 content 可能不是纯字符串，这里统一转成 JSON 文本。
            output = json.dumps(output, ensure_ascii=False)

        usage = {
            "input_tokens": getattr(response.usage, "input_tokens", 0),
            "output_tokens": getattr(response.usage, "output_tokens", 0),
            "total_tokens": getattr(response.usage, "total_tokens", 0),
        }

        return {
            "content": output,
            "model": self._settings.qwen_model,
            "usage": usage,
        }
