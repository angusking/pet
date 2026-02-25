package com.pet.api.ai.dto;

import java.time.LocalDateTime;

public record AiChatMessageResponse(
    Long id,
    Long sessionId,
    String role,
    String content,
    String model,
    Integer tokens,
    LocalDateTime createdAt
) {}
