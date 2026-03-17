"""大模型 Provider 抽象接口。"""

from abc import ABC, abstractmethod


class BaseLLMProvider(ABC):
    """统一的大模型调用接口。"""

    @abstractmethod
    def chat(self, messages: list[dict[str, str]]) -> dict:
        """执行一次对话调用并返回统一结构。"""
