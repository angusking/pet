package com.pet.api.pet.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PetWeightRecordCreateRequest(
    @NotNull(message = "weight value is required")
    @DecimalMin(value = "0.01", message = "weight value must be greater than 0")
    BigDecimal weightValue,
    @Pattern(regexp = "^(kg)?$", message = "unit must be kg")
    String unit,
    @Pattern(regexp = "^(home|clinic|other)?$", message = "source must be home, clinic or other")
    String source,
    @Size(max = 255, message = "note length must be <= 255")
    String note,
    @NotNull(message = "recordedAt is required")
    LocalDateTime recordedAt
) {}
