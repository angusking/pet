package com.pet.api.pet.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PetWeightRecordResponse(
    Long id,
    Long petId,
    BigDecimal weightValue,
    String unit,
    String source,
    String note,
    LocalDateTime recordedAt,
    LocalDateTime createdAt,
    BigDecimal changeFromPrevious
) {}
