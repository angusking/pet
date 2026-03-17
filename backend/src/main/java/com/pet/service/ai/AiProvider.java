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

  record AiReply(
      String content,
      String model,
      Integer tokens,
      String requestId
  ) {
    public AiReply(String content, String model, Integer tokens) {
      this(content, model, tokens, null);
    }
  }
}
