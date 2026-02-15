package com.pet.api.pet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PetCreateRequest(
    @NotBlank(message = "pet name is required") @Size(max = 50, message = "pet name length must be <= 50") String name,
    @Size(max = 50, message = "breed length must be <= 50") String breed,
    Integer gender,
    LocalDate birthday,
    BigDecimal weightKg,
    @NotBlank(message = "pet avatar is required") String avatarUrl,
    List<String> tags
) {}
