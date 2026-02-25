package com.pet.api.ai.dto;

import java.time.LocalDateTime;

public record AiChatSessionResponse(
    Long id,
    String title,
    Long petId,
    AiPetContextResponse pet,
    String lastMessagePreview,
    LocalDateTime updatedAt,
    LocalDateTime createdAt
) {}
