"""RAG 检索能力。

V1 不接入向量数据库，因此这里只提供占位实现。
这样未来接知识库时，不需要改编排主流程。
"""


class RagService:
    """检索增强服务。"""

    async def retrieve(self, query: str) -> str | None:
        """根据问题检索知识片段。

        当前版本暂不启用真实检索能力，因此返回 None。
        """
        return None
