package com.pet.service.ai;

import com.pet.entity.PetEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockAiProvider implements AiProvider {
  @Override
  public AiReply generateReply(AiRequest request) {
    PetEntity pet = request.pet();
    String answer = "这是 AI 助手的 Mock 回复。建议先记录症状或行为发生的时间、频率，以及饮食变化。";
    if (pet != null && pet.getName() != null && !pet.getName().isBlank()) {
      answer = "关于 " + pet.getName() + "，建议先记录症状或行为发生的时间、频率，以及饮食变化。";
    }
    String content = """
        {
          "requestId": "%s",
          "answer": "%s",
          "riskLevel": "low",
          "checklist": ["记录持续时间", "观察精神和食欲变化"],
          "services": [],
          "followUps": ["症状持续多久了？", "最近是否更换食物或环境？"],
          "disclaimer": "本回答仅供宠物日常护理参考，不能替代执业兽医诊断。"
        }
        """.formatted(
        request.requestId(),
        escapeJson(answer + " 你的问题是：" + request.userMessage()));
    return new AiReply(content, "mock-ai-v1", Math.max(12, request.userMessage().length() / 2), request.requestId());
  }

  private String escapeJson(String value) {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\r", "\\r")
        .replace("\n", "\\n");
  }
}
