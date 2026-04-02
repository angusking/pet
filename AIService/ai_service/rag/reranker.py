"""轻量级本地重排器。

当前 RAG 已经具备基础向量召回能力，但在中文长文本场景下，
单纯依赖 embedding 相似度，容易把“语义相近但主题不够聚焦”的分块排在前面。

这里增加一个非常轻量的本地重排层，目标不是替代专业 reranker，
而是做两件事：
1. 利用查询词和标题/章节/正文的字面重合，提升更“直答问题”的片段；
2. 保留原始向量分数作为基础项，避免把纯关键词命中但语义很偏的片段顶得过高。

当前实现不依赖额外模型，适合先在线上稳定跑起来。
"""

from __future__ import annotations

import re

from ai_service.rag.schemas import RetrievedChunk


class LightweightReranker:
    """基于关键词重合和字段权重的轻量重排器。

    它的定位非常明确：
    - 不是替代专业 Cross-Encoder reranker；
    - 而是在“不额外引入大模型成本”的前提下，
      把向量召回结果重新排得更符合当前问题主题。

    当前特别适合解决的场景是：
    - 向量召回语义接近，但主题不够聚焦；
    - 图书型知识库里，章节标题比正文更能代表主题；
    - 中文问题里存在“长期只吃肉/怎么喂/为什么不能”这类短语，需要字面命中增强。
    """

    _STOP_TERMS = {
        "为什么",
        "不能",
        "长期",
        "什么",
        "怎么",
        "一下",
        "一下子",
        "请问",
        "一下吗",
        "一下呢",
    }
    _FEEDING_QUERY_HINTS = ("吃", "喂", "饮食", "食物", "肉", "营养")
    _FEEDING_CHUNK_HINTS = (
        "喂养",
        "养育",
        "营养",
        "饲料",
        "食物",
        "蛋白质",
        "脂肪",
        "维生素",
        "矿物质",
        "碳水化合物",
        "饮水",
    )

    def rerank(self, query: str, chunks: list[RetrievedChunk], top_k: int) -> list[RetrievedChunk]:
        """对向量召回结果做轻量重排。

        规则设计：
        - 原始向量分数保留为基础分；
        - 查询中的中文短语命中标题/章节时，给予更高加权；
        - 命中正文时给予次级加权；
        - 最后只返回 top_k。
        """

        if not chunks:
            return []

        query_terms = self._extract_query_terms(query)
        if not query_terms:
            return chunks[:top_k]

        hint_terms = self._build_hint_terms(query)
        ranked = sorted(
            chunks,
            key=lambda chunk: self._score_chunk(query_terms=query_terms, hint_terms=hint_terms, chunk=chunk),
            reverse=True,
        )
        return ranked[:top_k]

    def _score_chunk(self, query_terms: list[str], hint_terms: list[str], chunk: RetrievedChunk) -> float:
        """给单个候选片段打综合分。

        分数组成大致分为三层：
        1. 原始向量分：保留语义检索的基本盘；
        2. 标题/章节命中加权：让主题更聚焦的片段靠前；
        3. 业务启发式修正：对喂养、营养等问题做轻量领域偏置。
        """

        score = float(chunk.score)

        title_text = self._normalize(
            " ".join(
                filter(
                    None,
                    [
                        chunk.title,
                        chunk.category,
                        chunk.part_title,
                        chunk.chapter_title,
                        chunk.section_title,
                        chunk.subtopic_title,
                    ],
                )
            )
        )
        body_text = self._normalize(chunk.text)

        matched_title_terms: set[str] = set()
        matched_body_terms: set[str] = set()
        for term in query_terms:
            if term in title_text:
                matched_title_terms.add(term)
            if term in body_text:
                matched_body_terms.add(term)

        score += min(len(matched_title_terms), 4) * 1.8
        score += min(len(matched_body_terms), 5) * 0.7

        matched_title_hints: set[str] = set()
        matched_body_hints: set[str] = set()
        for term in hint_terms:
            if term in title_text:
                matched_title_hints.add(term)
            if term in body_text:
                matched_body_hints.add(term)

        score += min(len(matched_title_hints), 4) * 1.8
        score += min(len(matched_body_hints), 4) * 0.45

        # 如果问题明显在问喂养/营养，但片段标题只落在“疾病”而没有任何喂养线索，
        # 则适度降权，避免“长期/不能”这类泛词把疾病章节顶到最前面。
        if hint_terms and "疾病" in title_text and not any(term in title_text for term in hint_terms):
            score -= 0.8

        # 对“吃什么/怎么喂/长期吃肉”这类喂养问题，进一步偏向喂养、养育、饲料配制章节。
        if hint_terms and any(term in title_text for term in ("喂养", "养育", "饲料", "配制")):
            score += 1.4
        if hint_terms and "疾病" in title_text:
            score -= 0.6

        # 匹配项越丰富，说明片段和问题的聚焦程度越高。
        score += len(matched_title_terms | matched_body_terms) * 0.25

        # 整句直接命中标题或正文时，说明主题非常接近。
        compact_query = self._normalize(query_terms[0]) if query_terms else ""
        if compact_query and compact_query in title_text:
            score += 1.5
        if compact_query and compact_query in body_text:
            score += 0.8

        return score

    def _extract_query_terms(self, query: str) -> list[str]:
        """从中文问题中提取可用于匹配的短语。

        当前策略：
        - 保留长度 >= 2 的中文连续片段
        - 同时生成 2~4 字的滑动窗口短语，增强对“长期只吃肉/只吃肉/吃肉”等局部主题的捕捉
        """

        normalized = self._normalize(query)
        if not normalized:
            return []

        chinese_only = re.sub(r"[^\u4e00-\u9fff]", "", normalized)
        terms: list[str] = []

        if len(chinese_only) >= 2:
            terms.append(chinese_only)

        for size in range(2, 5):
            for index in range(0, max(0, len(chinese_only) - size + 1)):
                term = chinese_only[index : index + size]
                if term in self._STOP_TERMS:
                    continue
                terms.append(term)

        # 去重并保留顺序。
        deduped: list[str] = []
        seen: set[str] = set()
        for term in terms:
            if len(term) < 2:
                continue
            if term in seen:
                continue
            seen.add(term)
            deduped.append(term)
        return deduped

    def _build_hint_terms(self, query: str) -> list[str]:
        """根据问题语义注入一组领域提示词。

        这里本质上是在做“极轻量的规则扩展词”：
        用户问题里不一定会直接出现“蛋白质/脂肪/维生素”，
        但如果已经判断是喂养问题，就可以把这些主题词加入重排考虑。
        """

        normalized = self._normalize(query)
        if not any(term in normalized for term in self._FEEDING_QUERY_HINTS):
            return []
        return list(self._FEEDING_CHUNK_HINTS)

    def _normalize(self, text: str) -> str:
        lowered = text.lower().strip()
        lowered = re.sub(r"\s+", "", lowered)
        return lowered
