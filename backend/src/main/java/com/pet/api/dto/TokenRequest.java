package com.pet.api.dto;

import jakarta.validation.constraints.NotBlank;

public record TokenRequest(
    @NotBlank(message = "username is required") String username
) {}
