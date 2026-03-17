"""大模型 Provider 抽象接口。

这一层的目的是把“编排逻辑”和“具体模型 SDK”解耦。
这样以后如果从 Qwen 切到其他模型，ChatOrchestrator 不需要大改。
"""

from abc import ABC, abstractmethod


class BaseLLMProvider(ABC):
    """统一的大模型调用接口。"""

    @abstractmethod
    def chat(self, messages: list[dict[str, str]]) -> dict:
        """执行一次对话调用并返回统一结构。

        返回值约定至少包含：
        - content: 模型输出文本
        - model: 实际使用的模型名
        - usage: token 统计
        """
