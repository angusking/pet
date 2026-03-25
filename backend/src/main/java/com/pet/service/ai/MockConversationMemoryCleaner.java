package com.pet.service.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Mock 模式下没有真正的 AIService Redis 需要清理，这里直接空实现。
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockConversationMemoryCleaner implements AiConversationMemoryCleaner {
  @Override
  public void clearConversationMemory(String conversationId, Long userId) {
    // Mock provider 下没有外部 AIService 短期记忆，不需要额外清理。
  }
}
