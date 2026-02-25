package com.pet.service.ai;

import com.pet.entity.PetEntity;

public interface AiProvider {
  AiReply generateReply(Long userId, PetEntity pet, String userMessage);

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
