package com.pet.api.ai.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 提供给 AIService 的体重分析输入数据。
 *
 * <p>这个结构是“给 AI 内部工具消费”的，不是前端展示模型。
 * 目标是让 AIService 一次拿到：
 * 1. 当前宠物基础信息
 * 2. 最近一次、前一次体重摘要
 * 3. 最近若干条体重记录
 *
 * <p>这样 AIService 无需再拼多次请求，也不需要重复推断 supportLevel。
 */
public record AiPetWeightAnalysisDataResponse(
    Long petId,
    String petName,
    String categoryPath,
    String displaySpecies,
    String supportLevel,
    String categoryWeightHint,
    BigDecimal currentWeight,
    LocalDateTime latestRecordedAt,
    BigDecimal previousWeight,
    BigDecimal changeFromPrevious,
    Integer recordCount,
    List<AiPetWeightRecordResponse> records
) {}
