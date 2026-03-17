"""安全控制能力。

在宠物健康场景下，AI 不能只追求“看起来会回答”，
还必须有基本的风险边界和保守策略。
"""

from ai_service.schemas.chat_response import ChatResponse, RiskLevel


class SafetyService:
    """安全规则处理服务。"""

    def enforce(self, response: ChatResponse, original_query: str) -> ChatResponse:
        """对模型输出进行基础安全修正。"""
        high_risk_keywords = ["抽搐", "昏迷", "便血", "呼吸困难", "持续呕吐", "高烧"]
        if any(keyword in original_query for keyword in high_risk_keywords):
            response.riskLevel = RiskLevel.HIGH
            if not any("就医" in item for item in response.checklist):
                response.checklist.append("尽快联系宠物医院或执业兽医进行线下检查")

        if not response.disclaimer.strip():
            response.disclaimer = "本回答仅供宠物日常护理参考，不能替代执业兽医诊断。"

        return response
