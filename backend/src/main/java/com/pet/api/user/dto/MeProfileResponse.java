package com.pet.api.user.dto;

public record MeProfileResponse(
    Long id,
    String username,
    String nickname,
    String avatarUrl
) {}
