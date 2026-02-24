package com.pet.api.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PostCommentCreateRequest(
    @NotBlank(message = "评论内容不能为空")
    @Size(max = 500, message = "评论内容不能超过500字")
    String content
) {}
