package com.pet.api.auth.dto;

public record AuthResponse(String accessToken, long expiresIn) {}
