"""应用异常定义。

把自定义异常集中在这里，有两个好处：
- 业务层抛错时语义更清楚
- 以后如果要统一映射 HTTP 错误，更容易集中处理
"""


class AIServiceError(Exception):
    """AIService 业务异常基类。"""


class LLMInvocationError(AIServiceError):
    """大模型调用异常。

    用于表示：
    - key 未配置
    - key 无效
    - 模型返回非 200
    - 调用链路异常
    """
