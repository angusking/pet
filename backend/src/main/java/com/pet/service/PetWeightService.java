package com.pet.service;

import com.pet.api.error.ApiError;
import com.pet.api.error.BusinessException;
import com.pet.api.ai.dto.AiPetWeightAnalysisDataResponse;
import com.pet.api.ai.dto.AiPetWeightRecordResponse;
import com.pet.api.pet.dto.PetWeightRecordCreateRequest;
import com.pet.api.pet.dto.PetWeightRecordListResponse;
import com.pet.api.pet.dto.PetWeightRecordResponse;
import com.pet.api.pet.dto.PetWeightSummaryResponse;
import com.pet.entity.PetEntity;
import com.pet.entity.PetWeightRecordEntity;
import com.pet.repository.PetRepository;
import com.pet.repository.PetWeightRecordRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PetWeightService {
  private static final String DEFAULT_UNIT = "kg";
  private static final String SUPPORT_PRECISE = "precise";
  private static final String SUPPORT_TREND_ONLY = "trend_only";
  private static final BigDecimal TREND_DELTA_THRESHOLD = new BigDecimal("0.05");

  private final PetRepository petRepository;
  private final PetWeightRecordRepository petWeightRecordRepository;
  private final PetService petService;

  public PetWeightService(
      PetRepository petRepository,
      PetWeightRecordRepository petWeightRecordRepository,
      PetService petService) {
    this.petRepository = petRepository;
    this.petWeightRecordRepository = petWeightRecordRepository;
    this.petService = petService;
  }

  @Transactional(readOnly = true)
  public PetWeightRecordListResponse listRecords(Long userId, Long petId) {
    PetEntity pet = requirePet(userId, petId);
    List<PetWeightRecordEntity> records = petWeightRecordRepository.findByPetIdAndUserIdOrderByRecordedAtDescIdDesc(
        petId,
        userId);
    return new PetWeightRecordListResponse(buildSummary(pet, records), mapRecordResponses(records));
  }

  /**
   * 提供给 AIService 的内部体重查询接口。
   *
   * <p>这里和前端接口最大的区别是：
   * - 只返回 AI 真正分析需要的数据
   * - 默认限制最近 N 条记录，避免无意义地把全量历史都喂给 AI
   * - 顺手补齐 supportLevel 和提示文案，减少 AIService 侧重复规则
   */
  @Transactional(readOnly = true)
  public AiPetWeightAnalysisDataResponse getWeightAnalysisDataForAi(Long userId, Long petId, Integer limit) {
    PetEntity pet = requirePet(userId, petId);
    List<PetWeightRecordEntity> allRecords = petWeightRecordRepository.findByPetIdAndUserIdOrderByRecordedAtDescIdDesc(
        petId,
        userId);
    int resolvedLimit = normalizeAiLimit(limit);
    List<PetWeightRecordEntity> records = allRecords.size() <= resolvedLimit
        ? allRecords
        : allRecords.subList(0, resolvedLimit);

    PetWeightRecordEntity latest = records.isEmpty() ? null : records.get(0);
    PetWeightRecordEntity previous = records.size() > 1 ? records.get(1) : null;
    String supportLevel = resolveSupportLevel(pet.getCategoryPath());

    return new AiPetWeightAnalysisDataResponse(
        pet.getId(),
        pet.getName(),
        pet.getCategoryPath(),
        pet.getBreed(),
        supportLevel,
        buildCategoryWeightHint(pet.getCategoryPath(), supportLevel),
        latest == null ? pet.getCurrentWeight() : latest.getWeightValue(),
        latest == null ? null : latest.getRecordedAt(),
        previous == null ? null : previous.getWeightValue(),
        previous == null ? null : subtract(latest.getWeightValue(), previous.getWeightValue()),
        records.size(),
        records.stream().map(this::toAiRecordResponse).toList());
  }

  /**
   * 新增体重记录后同步刷新 pets.currentWeight，保证现有首页、宠物卡片等展示无需额外改动即可拿到最新值。
   */
  @Transactional
  public PetWeightRecordResponse createRecord(Long userId, Long petId, PetWeightRecordCreateRequest request) {
    PetEntity pet = requirePet(userId, petId);

    PetWeightRecordEntity record = new PetWeightRecordEntity();
    record.setPetId(petId);
    record.setUserId(userId);
    record.setWeightValue(scaleWeight(request.weightValue()));
    record.setUnit(normalizeUnit(request.unit()));
    record.setSource(normalizeText(request.source()));
    record.setNote(normalizeText(request.note()));
    record.setRecordedAt(request.recordedAt());

    PetWeightRecordEntity saved = petWeightRecordRepository.save(record);
    refreshCurrentWeight(pet);
    return toResponse(saved, findPreviousOlderRecord(saved));
  }

  @Transactional
  public void deleteRecord(Long userId, Long petId, Long recordId) {
    PetEntity pet = requirePet(userId, petId);
    PetWeightRecordEntity record = petWeightRecordRepository.findByIdAndPetIdAndUserId(recordId, petId, userId)
        .orElseThrow(() -> new BusinessException(ApiError.PET_WEIGHT_RECORD_NOT_FOUND, HttpStatus.NOT_FOUND));
    petWeightRecordRepository.delete(record);
    refreshCurrentWeight(pet);
  }

  private PetEntity requirePet(Long userId, Long petId) {
    return petRepository.findByIdAndUserId(petId, userId)
        .orElseThrow(() -> new BusinessException(ApiError.PET_NOT_FOUND, HttpStatus.NOT_FOUND));
  }

  private List<PetWeightRecordResponse> mapRecordResponses(List<PetWeightRecordEntity> records) {
    List<PetWeightRecordResponse> responses = new ArrayList<>();
    PetWeightRecordEntity olderRecord = null;
    for (int i = records.size() - 1; i >= 0; i--) {
      PetWeightRecordEntity current = records.get(i);
      responses.add(0, toResponse(current, olderRecord));
      olderRecord = current;
    }
    return responses;
  }

  private PetWeightSummaryResponse buildSummary(PetEntity pet, List<PetWeightRecordEntity> records) {
    String supportLevel = resolveSupportLevel(pet.getCategoryPath());
    if (records.isEmpty()) {
      return new PetWeightSummaryResponse(
          pet.getCurrentWeight(),
          null,
          null,
          null,
          null,
          "no_data",
          supportLevel,
          buildCategoryWeightHint(pet.getCategoryPath(), supportLevel));
    }

    PetWeightRecordEntity latest = records.get(0);
    PetWeightRecordEntity previous = records.size() > 1 ? records.get(1) : null;
    BigDecimal changeFromPrevious = previous == null ? null : subtract(latest.getWeightValue(), previous.getWeightValue());
    BigDecimal changeIn30Days = calculateChangeIn30Days(records);

    return new PetWeightSummaryResponse(
        latest.getWeightValue(),
        latest.getRecordedAt(),
        previous == null ? null : previous.getWeightValue(),
        changeFromPrevious,
        changeIn30Days,
        resolveTrendDirection(changeFromPrevious),
        supportLevel,
        buildCategoryWeightHint(pet.getCategoryPath(), supportLevel));
  }

  /**
   * 30天变化不追求复杂算法，只拿“最近一条”和“30天窗口内最早一条”做差，便于页面快速展示。
   */
  private BigDecimal calculateChangeIn30Days(List<PetWeightRecordEntity> records) {
    PetWeightRecordEntity latest = records.get(0);
    PetWeightRecordEntity baseline = null;
    for (int i = records.size() - 1; i >= 0; i--) {
      PetWeightRecordEntity candidate = records.get(i);
      if (!candidate.getRecordedAt().isBefore(latest.getRecordedAt().minusDays(30))) {
        baseline = candidate;
        break;
      }
    }
    if (baseline == null || baseline.getId().equals(latest.getId())) {
      return null;
    }
    return subtract(latest.getWeightValue(), baseline.getWeightValue());
  }

  private PetWeightRecordResponse toResponse(PetWeightRecordEntity record, PetWeightRecordEntity olderRecord) {
    return new PetWeightRecordResponse(
        record.getId(),
        record.getPetId(),
        record.getWeightValue(),
        record.getUnit(),
        record.getSource(),
        record.getNote(),
        record.getRecordedAt(),
        record.getCreatedAt(),
        olderRecord == null ? null : subtract(record.getWeightValue(), olderRecord.getWeightValue()));
  }

  /**
   * AI 内部工具不需要前端那些展示专用字段，所以单独映射一份更轻的记录结构。
   */
  private AiPetWeightRecordResponse toAiRecordResponse(PetWeightRecordEntity record) {
    return new AiPetWeightRecordResponse(
        record.getWeightValue(),
        record.getUnit(),
        record.getSource(),
        record.getNote(),
        record.getRecordedAt());
  }

  private PetWeightRecordEntity findPreviousOlderRecord(PetWeightRecordEntity saved) {
    return petWeightRecordRepository.findFirstByPetIdAndUserIdAndRecordedAtBeforeOrderByRecordedAtDescIdDesc(
        saved.getPetId(),
        saved.getUserId(),
        saved.getRecordedAt())
        .orElse(null);
  }

  private void refreshCurrentWeight(PetEntity pet) {
    BigDecimal currentWeight = petWeightRecordRepository.findFirstByPetIdAndUserIdOrderByRecordedAtDescIdDesc(
        pet.getId(),
        pet.getUserId())
        .map(PetWeightRecordEntity::getWeightValue)
        .orElse(null);
    pet.setCurrentWeight(currentWeight);
    petRepository.save(pet);

    if (Boolean.TRUE.equals(pet.getIsPrimary())) {
      petService.syncCurrentPrimaryToLoginState(pet.getUserId());
    }
  }

  private String resolveTrendDirection(BigDecimal changeFromPrevious) {
    if (changeFromPrevious == null) {
      return "insufficient_data";
    }
    if (changeFromPrevious.compareTo(TREND_DELTA_THRESHOLD) >= 0) {
      return "up";
    }
    if (changeFromPrevious.compareTo(TREND_DELTA_THRESHOLD.negate()) <= 0) {
      return "down";
    }
    return "stable";
  }

  /**
   * 猫狗兔雪貂先走“可做相对精确参考”的路线，其它类别先采用保守的“只看连续趋势”。
   */
  private String resolveSupportLevel(String categoryPath) {
    if (categoryPath == null || categoryPath.isBlank()) {
      return SUPPORT_TREND_ONLY;
    }
    if (categoryPath.startsWith("cat")
        || categoryPath.startsWith("dog")
        || categoryPath.startsWith("rabbit")
        || categoryPath.startsWith("ferret")) {
      return SUPPORT_PRECISE;
    }
    return SUPPORT_TREND_ONLY;
  }

  private String buildCategoryWeightHint(String categoryPath, String supportLevel) {
    if (SUPPORT_PRECISE.equals(supportLevel)) {
      if (categoryPath != null && categoryPath.startsWith("cat")) {
        return "猫类可结合年龄、绝育情况和连续记录观察体重变化，单次偏差不直接等于健康结论。";
      }
      if (categoryPath != null && categoryPath.startsWith("dog")) {
        return "犬类需要结合体型层级一起判断，建议重点看近30天连续趋势，不只看单次数字。";
      }
      if (categoryPath != null && categoryPath.startsWith("rabbit")) {
        return "兔类体重变化应结合食欲和排便观察，持续下降比单次偏轻更值得警惕。";
      }
      return "该类别可结合分类与连续记录做参考判断，但结果仍只用于日常观察。";
    }
    return "该宠物类别个体差异较大，当前阶段建议优先观察连续体重趋势，不建议只根据单次体重做判断。";
  }

  private BigDecimal subtract(BigDecimal newer, BigDecimal older) {
    return newer.subtract(older).setScale(2, RoundingMode.HALF_UP);
  }

  private BigDecimal scaleWeight(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_UP);
  }

  private String normalizeUnit(String unit) {
    return DEFAULT_UNIT;
  }

  /**
   * AI 分析只需要最近少量样本即可，默认 10 条，上限也控制在 10 条。
   * 这样既能看趋势，又不会把过长历史塞给模型。
   */
  private int normalizeAiLimit(Integer requestedLimit) {
    if (requestedLimit == null || requestedLimit <= 0) {
      return 10;
    }
    return Math.min(requestedLimit, 10);
  }

  private String normalizeText(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
