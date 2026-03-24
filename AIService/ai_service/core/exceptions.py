"""应用异常定义。

把自定义异常集中放在这里，有两个目的：
1. 让不同层抛出的异常语义更清楚
2. 后续如果要统一映射 HTTP 错误，会更容易集中处理
"""


class AIServiceError(Exception):
    """AIService 业务异常基类。"""


class LLMInvocationError(AIServiceError):
    """大模型调用异常。"""


class ToolInvocationError(AIServiceError):
    """Tool 调用异常。

    包含两类典型场景：
    1. Tool 本身找不到或输入不合法
    2. Tool 访问下游依赖失败，例如 Java 后端内部接口不可用
    """
