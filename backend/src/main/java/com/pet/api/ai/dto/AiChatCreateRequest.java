package com.pet.api.ai.dto;

public record AiChatCreateRequest(
    Long petId,
    String title
) {}
