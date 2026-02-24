package com.pet.api.post.dto;

import java.time.LocalDateTime;

public record PostCommentResponse(
    Long id,
    Long postId,
    Long userId,
    String content,
    LocalDateTime createdAt,
    PostAuthorSummary author
) {}
