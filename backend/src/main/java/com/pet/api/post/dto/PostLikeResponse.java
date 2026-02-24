package com.pet.api.post.dto;

public record PostLikeResponse(
    boolean liked,
    int likeCount
) {}
