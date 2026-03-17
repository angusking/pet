"""问题重写能力。

V1 先提供最保守的实现：

- 默认返回原问题
- 保留独立模块和调用位置
- 为后续接入更强的重写模型预留扩展点
"""


class RewriteService:
    """问题重写服务。"""

    def rewrite(self, query: str) -> str:
        """返回重写后的问题。"""
        return query.strip()
