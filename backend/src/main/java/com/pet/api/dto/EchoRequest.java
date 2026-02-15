package com.pet.api.dto;

import jakarta.validation.constraints.NotBlank;

public record EchoRequest(
    @NotBlank(message = "message is required") String message
) {}
