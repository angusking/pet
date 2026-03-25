package com.pet.api.ai;

import com.pet.api.ai.dto.AiPetWeightRecordsResponse;
import com.pet.service.PetWeightService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供给 AIService 使用的宠物内部接口。
 *
 * <p>这些接口不直接面向前端，而是面向内部 AI 编排服务。
 * 当前先提供体重分析所需的查询能力，后续地点、服务、用品等工具也建议沿用这一层。
 */
@RestController
@RequestMapping("/internal/ai/pets")
public class AiInternalPetController {
  private final PetWeightService petWeightService;

  public AiInternalPetController(PetWeightService petWeightService) {
    this.petWeightService = petWeightService;
  }

  /**
   * 查询指定宠物最近若干条体重记录，供 AI 内部工具做趋势分析。
   *
   * <p>这里仍然要求传入 userId 做归属校验，避免 AIService 越权读取其他用户的宠物数据。
   */
  @GetMapping("/{petId}/weight-records")
  public AiPetWeightRecordsResponse getWeightRecords(
      @PathVariable("petId") Long petId,
      @RequestParam("userId") Long userId,
      @RequestParam(name = "limit", defaultValue = "10") Integer limit) {
    return petWeightService.getWeightRecordsForAi(userId, petId, limit);
  }
}
