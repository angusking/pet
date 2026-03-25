package com.pet.api.ai.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AiChatMessageResponse(
    Long id,
    Long sessionId,
    String role,
    String content,
    String model,
    Integer tokens,
    LocalDateTime createdAt,
    Boolean followUp,
    String intent,
    String riskLevel,
    List<String> checklist,
    List<ServiceItem> services,
    List<String> followUps,
    List<String> followUpQuestions,
    List<ActionCard> actionCards,
    String disclaimer
) {
  public record ServiceItem(
      String name,
      String description,
      String url
  ) {}

  public record ActionCard(
      String title,
      List<String> items
  ) {}
}
