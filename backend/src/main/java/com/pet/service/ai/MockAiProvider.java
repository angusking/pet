package com.pet.service.ai;

import com.pet.entity.PetEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockAiProvider implements AiProvider {
  @Override
  public AiReply generateReply(Long userId, PetEntity pet, String userMessage) {
    String petLine = pet == null ? "当前未关联宠物。" : ("当前宠物：" + pet.getName() + "（" + (pet.getBreed() == null ? "未知品种" : pet.getBreed()) + "）。");
    String content = "这是 AI 助手的 Mock 回复。\n" + petLine + "\n你的问题是：" + userMessage + "\n建议先记录症状/行为发生时间、频率和饮食变化。";
    return new AiReply(content, "mock-ai-v1", Math.max(12, userMessage.length() / 2));
  }
}
