"""Question Rewrite 的规则层实现。

第一版规则层优先覆盖最确定的几类场景：
1. 体重分析
2. 体重分析追问
3. 地点搜索
4. 通用知识型问题

规则层的目标不是理解全部问题，而是：
- 先拿下高置信度场景，减少不必要的 LLM 调用；
- 尽量提取稳定的结构化槽位；
- 给后续 DecisionService 和 Tool 提供更可控的输入。
"""

from __future__ import annotations

import re

from ai_service.schemas.chat_request import ChatRequest
from ai_service.schemas.question_rewrite import (
    QuestionRewriteResult,
    RewriteIntent,
    RewriteSource,
)


class QuestionRewriteRules:
    """基于规则做第一轮问题标准化。"""

    _WEIGHT_KEYWORDS = ("体重", "胖", "瘦", "增重", "减重", "趋势")
    _FOLLOW_UP_OBSERVATION_KEYWORDS = (
        "食欲",
        "精神",
        "活动量",
        "饮食",
        "换粮",
        "加餐",
        "零食",
        "排便",
        "喝水",
    )
    _LOCATION_INTENT_KEYWORDS = (
        "附近",
        "哪里",
        "在哪",
        "离我近",
        "地址",
        "到哪",
        "怎么去",
    )
    _LOCATION_PLACE_KEYWORDS = (
        "24小时宠物医院",
        "宠物医院",
        "宠物诊所",
        "宠物店",
        "宠物门店",
        "宠物洗护",
        "洗护门店",
        "宠物寄养",
        "急诊医院",
        "医院",
        "门店",
    )
    _GENERAL_KNOWLEDGE_PATTERNS = (
        "为什么",
        "怎么回事",
        "是什么原因",
        "能不能",
        "可不可以",
        "是否可以",
    )
    _RESET_ANALYSIS_PATTERNS = ("重新总结", "重新分析", "综合判断", "重新判断")

    def rewrite(
        self,
        request: ChatRequest,
        context_messages: list[dict],
    ) -> QuestionRewriteResult | None:
        """尝试用规则直接给出高置信重写结果。"""

        query = (request.message or "").strip()
        if not query:
            return None

        pet_id = request.pet.petId if request.pet is not None else None
        last_assistant_text = self._find_last_assistant_text(context_messages).lower()

        if self._is_weight_follow_up(query=query, last_assistant_text=last_assistant_text):
            return QuestionRewriteResult(
                originalQuestion=query,
                normalizedQuestion=self._normalize_weight_follow_up(query),
                intentType=RewriteIntent.WEIGHT_FOLLOW_UP,
                suggestTool=pet_id is not None,
                suggestedToolName="weight_analysis" if pet_id is not None else None,
                followUp=True,
                needKnowledgeRetrieval=False,
                confidence=0.92 if pet_id is not None else 0.82,
                source=RewriteSource.RULE,
                reasoningTags=["follow_up_observation", "previous_weight_context"],
                extractedSlots={
                    "petId": pet_id,
                    "queryType": "observation_follow_up",
                },
            )

        if self._contains_any(query, self._WEIGHT_KEYWORDS):
            return QuestionRewriteResult(
                originalQuestion=query,
                normalizedQuestion=self._normalize_weight_question(query),
                intentType=RewriteIntent.WEIGHT_ANALYSIS,
                suggestTool=pet_id is not None,
                suggestedToolName="weight_analysis" if pet_id is not None else None,
                followUp=False,
                needKnowledgeRetrieval=False,
                confidence=0.95 if pet_id is not None else 0.72,
                source=RewriteSource.RULE,
                reasoningTags=[
                    "weight_keyword",
                    "has_pet_context" if pet_id is not None else "missing_pet_context",
                ],
                extractedSlots={
                    "petId": pet_id,
                    "queryType": "weight_analysis",
                },
            )

        if self._looks_like_location_search(query):
            location_text = self._extract_location_fragment(query)
            place_keyword = self._extract_place_keyword(query)
            has_explicit_location = bool(location_text)
            return QuestionRewriteResult(
                originalQuestion=query,
                normalizedQuestion=self._normalize_location_question(
                    query=query,
                    location_text=location_text,
                    place_keyword=place_keyword,
                ),
                intentType=RewriteIntent.LOCATION_SEARCH,
                suggestTool=True,
                suggestedToolName="location_search",
                followUp=False,
                needKnowledgeRetrieval=False,
                confidence=0.9 if has_explicit_location else 0.76,
                source=RewriteSource.RULE,
                reasoningTags=[
                    "location_keyword",
                    "has_explicit_location" if has_explicit_location else "missing_explicit_location",
                ],
                extractedSlots={
                    "locationText": location_text,
                    "placeKeyword": place_keyword,
                    "hasExplicitLocation": has_explicit_location,
                    "queryType": "location_search",
                },
            )

        if self._contains_any(query, self._GENERAL_KNOWLEDGE_PATTERNS):
            return QuestionRewriteResult(
                originalQuestion=query,
                normalizedQuestion=self._normalize_general_knowledge_question(query),
                intentType=RewriteIntent.GENERAL_KNOWLEDGE,
                suggestTool=False,
                suggestedToolName=None,
                followUp=False,
                needKnowledgeRetrieval=True,
                confidence=0.8,
                source=RewriteSource.RULE,
                reasoningTags=["general_knowledge_pattern"],
                extractedSlots={},
            )

        return None

    def _is_weight_follow_up(self, query: str, last_assistant_text: str) -> bool:
        """识别“体重分析后的补充观察信息”场景。"""

        if self._contains_any(query, self._RESET_ANALYSIS_PATTERNS):
            return False
        if not last_assistant_text:
            return False
        if "体重" not in last_assistant_text and "趋势" not in last_assistant_text:
            return False
        if not self._contains_any(query, self._FOLLOW_UP_OBSERVATION_KEYWORDS):
            return False
        # 规则层优先把“短句补充信息”识别成 follow-up，
        # 避免把“食欲正常，活动量下降”误判成全新问题。
        return len(query.strip()) <= 40

    def _normalize_weight_question(self, query: str) -> str:
        return (
            f"请标准化理解这个体重分析需求：{query}。"
            "重点判断用户是否在询问近期体重变化、胖瘦变化或连续趋势。"
        )

    def _normalize_weight_follow_up(self, query: str) -> str:
        return (
            f"用户正在补充与体重分析相关的观察信息：{query}。"
            "请将其理解为上一轮体重判断的增量上下文，而不是新的完整分析请求。"
        )

    def _normalize_location_question(self, query: str, location_text: str, place_keyword: str) -> str:
        if location_text and place_keyword:
            return f"请查找“{location_text}”附近与“{place_keyword}”相关的线下地点。原问题：{query}"
        if location_text:
            return f"请查找“{location_text}”附近相关线下地点。原问题：{query}"
        return f"用户想查询附近线下地点，但当前问题缺少明确地点信息。原问题：{query}"

    def _normalize_general_knowledge_question(self, query: str) -> str:
        return f"请把这个宠物通用知识问题标准化为可检索、可解释的问题：{query}"

    def _looks_like_location_search(self, query: str) -> bool:
        if self._contains_any(query, self._LOCATION_INTENT_KEYWORDS):
            return True
        # 没有明显位置意图词时，只在“地点类型 + 显式区域”同时出现的情况下，才判为地点搜索。
        return bool(self._extract_place_keyword(query) and self._extract_location_fragment(query))

    def _extract_location_fragment(self, query: str) -> str:
        """尽量从自然语言里抽取地点片段。

        规则比较保守：
        - 能明确提取就返回；
        - 提取不稳时返回空字符串，交给后续 LLM 或直接让用户补充。
        """

        compact = re.sub(r"\s+", "", query)
        working = compact
        for place_keyword in self._LOCATION_PLACE_KEYWORDS:
            working = working.replace(place_keyword, "")

        for prefix in ("请问", "帮我找", "帮我查", "查一下", "搜一下", "我想找", "想找"):
            if working.startswith(prefix):
                working = working[len(prefix) :]

        for marker in ("附近", "哪里", "在哪", "有没有", "有吗", "地址", "怎么去", "离我近", "到哪"):
            if marker in working:
                working = working.split(marker, 1)[0]
                break

        return working.strip("，。！？,.!?：: ")

    def _extract_place_keyword(self, query: str) -> str:
        for keyword in self._LOCATION_PLACE_KEYWORDS:
            if keyword in query:
                return keyword
        return ""

    def _find_last_assistant_text(self, context_messages: list[dict]) -> str:
        for message in reversed(context_messages):
            if not isinstance(message, dict):
                continue
            if str(message.get("role", "")).strip() == "assistant":
                return str(message.get("content", "")).strip()
        return ""

    def _contains_any(self, text: str, keywords: tuple[str, ...]) -> bool:
        return any(keyword in text for keyword in keywords)
