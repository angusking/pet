package com.pet.api.ai.dto;

public record AiPetContextResponse(
    Long id,
    String name,
    String breed,
    String avatarUrl
) {}
