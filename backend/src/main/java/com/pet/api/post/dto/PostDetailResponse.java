package com.pet.api.post.dto;

import java.time.LocalDateTime;
import java.util.List;

public record PostDetailResponse(
    Long id,
    Long userId,
    Long petId,
    String content,
    String city,
    List<String> tags,
    Integer likeCount,
    Integer commentCount,
    LocalDateTime createdAt,
    List<String> mediaUrls,
    boolean likedByMe,
    boolean canDelete,
    PostAuthorSummary author,
    PostPetSummary pet
) {}
