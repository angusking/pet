"""工具基类定义。

这一层只约束所有 Tool 的统一执行入口，不把具体业务判断塞进基类里。
后续无论是体重分析、地点查询还是服务查询，都沿用这套接口。
"""

from abc import ABC, abstractmethod
from typing import Any


class BaseTool(ABC):
    """所有 Tool 共享的最小接口。

    这里保持接口简单，编排层只需要关心：
    1. Tool 的名字是什么
    2. 如何以统一方式执行 Tool
    """

    name: str = "base_tool"

    @abstractmethod
    async def run(self, payload: dict[str, Any]) -> dict[str, Any]:
        """执行工具并返回结构化结果。"""
