package com.pet.service.ai;

import java.util.List;

public record AiServiceChatResponse(
    String requestId,
    String answer,
    String riskLevel,
    List<String> checklist,
    List<ServiceItem> services,
    List<String> followUps,
    String disclaimer
) {
  public record ServiceItem(
      String name,
      String description,
      String url
  ) {}
}
