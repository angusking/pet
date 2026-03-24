"""Tool 元信息定义。

这里不承载具体执行逻辑，只负责描述：
1. Tool 做什么
2. 什么场景适合调用
3. 需要哪些输入
4. 不该什么时候调用

这些信息既会被 Tool 注册表使用，也会被 PromptBuilder 转成第一轮决策提示词。
"""

from dataclasses import dataclass, field

from ai_service.tools.base import BaseTool


@dataclass(slots=True)
class ToolDefinition:
    """单个 Tool 的注册定义。"""

    name: str
    description: str
    when_to_use: list[str]
    required_inputs: list[str]
    when_not_to_use: list[str]
    tool: BaseTool | None = None
    enabled: bool = True
    notes: list[str] = field(default_factory=list)
