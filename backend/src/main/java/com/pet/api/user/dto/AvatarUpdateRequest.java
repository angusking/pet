package com.pet.api.user.dto;

import jakarta.validation.constraints.NotBlank;

public record AvatarUpdateRequest(
    @NotBlank(message = "头像地址不能为空") String avatarUrl
) {}
