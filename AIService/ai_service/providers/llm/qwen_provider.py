"""Qwen 调用封装。"""

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

        返回值字段：

        - content: 模型输出文本
        - model: 模型名称
        - usage: token 统计信息
        """
        if not self._settings.dashscope_api_key:
            raise LLMInvocationError("DASHSCOPE_API_KEY 未配置，无法调用 Qwen。")

        call_kwargs = {
            "model": self._settings.qwen_model,
            "api_key": self._settings.dashscope_api_key,
            "messages": messages,
            "result_format": "message",
        }

        # DashScope Python SDK 支持通过 `base_address` 覆盖默认 HTTP 地址。
        # 这里把业务配置名统一暴露为 DASHSCOPE_BASE_URL，方便和其他服务配置对齐。
        if self._settings.dashscope_base_url.strip():
            call_kwargs["base_address"] = self._settings.dashscope_base_url.strip()

        response = Generation.call(
            **call_kwargs,
        )

        if response.status_code != 200:
            raise LLMInvocationError(f"Qwen 调用失败: {response.message}")

        output = response.output.choices[0].message.content
        if not isinstance(output, str):
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
