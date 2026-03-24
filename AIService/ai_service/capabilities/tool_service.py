"""Tool 执行服务。

这一层只负责按名称找到 Tool 并执行，不再做“是否调用 Tool”的判断。
是否需要调用、调用哪个 Tool，统一交给第一轮决策服务处理。
"""

from typing import Any

from ai_service.core.exceptions import ToolInvocationError
from ai_service.core.settings import Settings
from ai_service.tools.registry import ToolRegistry


class ToolService:
    """根据注册表执行指定 Tool。"""

    def __init__(self, settings: Settings) -> None:
        self._registry = ToolRegistry(settings=settings)

    def build_registry_prompt_text(self) -> str:
        """返回当前启用 Tool 的提示词片段。"""
        return self._registry.build_registry_prompt_text()

    async def invoke(self, tool_name: str, tool_input: dict[str, Any]) -> dict[str, Any]:
        """按名称执行 Tool。"""
        definition = self._registry.get_tool(tool_name)
        if definition is None or definition.tool is None:
            raise ToolInvocationError(f"未找到可执行的 Tool: {tool_name}")
        return await definition.tool.run(tool_input)
