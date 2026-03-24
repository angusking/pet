package com.pet.api.pet.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PetWeightSummaryResponse(
    BigDecimal currentWeight,
    LocalDateTime latestRecordedAt,
    BigDecimal previousWeight,
    BigDecimal changeFromPrevious,
    BigDecimal changeIn30Days,
    String trendDirection,
    String categorySupportLevel,
    String categoryWeightHint
) {}
