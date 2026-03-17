"""应用异常定义。"""


class AIServiceError(Exception):
    """AIService 业务异常基类。"""


class LLMInvocationError(AIServiceError):
    """大模型调用异常。"""
