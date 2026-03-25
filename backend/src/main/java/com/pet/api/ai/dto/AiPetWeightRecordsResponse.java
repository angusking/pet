package com.pet.api.ai.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 提供给 AIService 的宠物体重原始记录响应。
 *
 * <p>这里刻意只返回“查询结果”，不提前做趋势分析，
 * 让 AIService 自己决定如何整理记录并交给 LLM 做分析。
 */
public record AiPetWeightRecordsResponse(
    Long petId,
    String petName,
    String categoryPath,
    String displaySpecies,
    LocalDate birthDate,
    String gender,
    Boolean neutered,
    BigDecimal currentWeight,
    Integer recordCount,
    List<AiPetWeightRecordItemResponse> records
) {}
