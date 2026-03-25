package com.pet.service.ai;

import java.util.List;

public record AiServiceChatResponse(
    String requestId,
    Boolean followUp,
    String intent,
    String answer,
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
