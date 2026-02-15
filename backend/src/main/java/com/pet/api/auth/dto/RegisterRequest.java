package com.pet.api.auth.dto;

import com.pet.api.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "用户名不能为空") @Size(max = 50, message = "用户名长度不能超过 50") String username,
    @NotBlank(message = "密码不能为空") @StrongPassword String password,
    @Size(max = 50) String nickname
) {}
