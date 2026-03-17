package com.pet.api.pet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PetCreateRequest(
    @NotBlank(message = "pet name is required")
    @Size(max = 50, message = "pet name length must be <= 50")
    String name,
    Long categoryId,
    @Size(max = 255, message = "custom species note length must be <= 255")
    String customSpeciesNote,
    @Size(max = 50, message = "breed length must be <= 50")
    String breed,
    @Pattern(
        regexp = "^(male|female|unknown)?$",
        message = "gender must be male, female or unknown")
    String gender,
    LocalDate birthDate,
    Boolean neutered,
    BigDecimal currentWeight,
    @NotBlank(message = "pet avatar is required")
    String avatarUrl,
    List<String> tags
) {}
