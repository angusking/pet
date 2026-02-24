package com.pet.api.post.dto;

public record PostAuthorSummary(
    Long id,
    String username,
    String nickname,
    String avatarUrl
) {}
