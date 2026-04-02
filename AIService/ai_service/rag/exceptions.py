"""RAG 相关异常定义。

单独拆出这一层的原因是：
1. 让知识库版本错误、索引构建错误、检索器未就绪等问题语义更明确；
2. 便于 API 层后续把不同错误稳定映射成不同 HTTP 状态码。
"""


class RagError(Exception):
    """RAG 领域异常基类。"""


class KnowledgeVersionNotFoundError(RagError):
    """指定的知识库版本不存在。"""


class KnowledgeVersionInvalidError(RagError):
    """指定版本存在，但知识文件或索引文件不完整。"""


class IndexBuildError(RagError):
    """索引构建失败。"""


class RetrieverNotReadyError(RagError):
    """检索器尚未加载任何可用版本。"""

