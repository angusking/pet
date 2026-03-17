"""工具基类定义。"""

from abc import ABC, abstractmethod


class BaseTool(ABC):
    """所有工具都应该遵循这个统一接口。"""

    name: str = "base_tool"

    @abstractmethod
    def run(self, payload: dict) -> dict:
        """执行工具并返回结构化结果。"""
