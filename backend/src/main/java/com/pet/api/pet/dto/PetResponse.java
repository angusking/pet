package com.pet.api.pet.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PetResponse(
    Long id,
    String name,
    String breed,
    Long categoryId,
    String categoryName,
    String categoryPath,
    String customSpeciesNote,
    String gender,
    LocalDate birthDate,
    Boolean neutered,
    BigDecimal currentWeight,
    String avatarUrl,
    boolean primary,
    List<String> tags
) {}
