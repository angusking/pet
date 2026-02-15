package com.pet.api.pet.dto;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;

public record PetResponse(
    Long id,
    String name,
    String breed,
    Integer gender,
    LocalDate birthday,
    BigDecimal weightKg,
    String avatarUrl,
    boolean primary,
    List<String> tags
) {}
