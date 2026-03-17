package com.pet.service.ai;

import java.util.List;

public record AiServiceChatRequest(
    String requestId,
    String conversationId,
    Long userId,
    PetInfo pet,
    String message,
    List<RecentMessage> recentMessages,
    BizData bizData
) {
  public record PetInfo(
      Long petId,
      String name,
      String type,
      Double age,
      Double weight
  ) {}

  public record RecentMessage(
      String role,
      String content
  ) {}

  public record BizData(
      List<Object> vaccines,
      List<Object> weightHistory
  ) {}
}
