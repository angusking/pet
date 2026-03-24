package com.pet.api.ai.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 提供给 AIService 使用的体重记录明细。
 *
 * <p>这里刻意保持字段轻量，只保留 AI 做趋势分析真正需要的内容：
 * 记录时间、体重值、来源、备注。
 * 不复用前端 DTO，是为了避免后续前端展示字段调整时影响内部 AI 契约。
 */
public record AiPetWeightRecordResponse(
    BigDecimal weightValue,
    String unit,
    String source,
    String note,
    LocalDateTime recordedAt
) {}
