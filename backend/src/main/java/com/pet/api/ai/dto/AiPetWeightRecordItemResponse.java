package com.pet.api.ai.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 提供给 AIService 的体重记录明细。
 */
public record AiPetWeightRecordItemResponse(
    BigDecimal weightValue,
    String unit,
    String source,
    String note,
    LocalDateTime recordedAt
) {}
