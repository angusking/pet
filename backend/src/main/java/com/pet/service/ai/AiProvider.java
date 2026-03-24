package com.pet.service.ai;

import com.pet.entity.PetEntity;
import java.util.List;

public interface AiProvider {
  AiReply generateReply(AiRequest request);

  record AiRequest(
      String requestId,
      String conversationId,
      Long userId,
      PetEntity pet,
      String userMessage,
      List<ChatMessage> recentMessages
  ) {}

  record ChatMessage(
      String role,
      String content
  ) {}

  /**
   * AI 提供方统一返回的内部结果。
   *
   * <p>content 只保存用户可直接展示的正文；
   * structuredPayload 保存完整结构化结果，供后端会话消息透传给前端渲染标签和卡片。
   */
  record AiReply(
      String content,
      String structuredPayload,
      String model,
      Integer tokens,
      String requestId
  ) {
    public AiReply(String content, String model, Integer tokens) {
      this(content, null, model, tokens, null);
    }
  }
}
