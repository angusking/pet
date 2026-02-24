package com.pet.api.post.dto;

public record PostPetSummary(
    Long id,
    String name,
    String breed,
    String avatarUrl
) {}
