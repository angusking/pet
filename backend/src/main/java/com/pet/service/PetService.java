package com.pet.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pet.api.error.ApiError;
import com.pet.api.error.BusinessException;
import com.pet.api.pet.dto.PetCreateRequest;
import com.pet.api.pet.dto.PetResponse;
import com.pet.entity.PetCategoryEntity;
import com.pet.entity.PetEntity;
import com.pet.repository.PetCategoryRepository;
import com.pet.repository.PetRepository;
import com.pet.repository.PetWeightRecordRepository;
import java.util.Collections;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PetService {
  private final PetRepository petRepository;
  private final PetCategoryRepository petCategoryRepository;
  private final PetWeightRecordRepository petWeightRecordRepository;
  private final ObjectMapper objectMapper;
  private final LoginUserStateService loginUserStateService;

  public PetService(
      PetRepository petRepository,
      PetCategoryRepository petCategoryRepository,
      PetWeightRecordRepository petWeightRecordRepository,
      ObjectMapper objectMapper,
      LoginUserStateService loginUserStateService) {
    this.petRepository = petRepository;
    this.petCategoryRepository = petCategoryRepository;
    this.petWeightRecordRepository = petWeightRecordRepository;
    this.objectMapper = objectMapper;
    this.loginUserStateService = loginUserStateService;
  }

  @Transactional(readOnly = true)
  public PetResponse getMyPrimaryPet(Long userId) {
    PetResponse cached = loginUserStateService.getCurrentPet(userId);
    if (cached != null) {
      return cached;
    }
    List<PetEntity> pets = petRepository.findByUserIdOrderByIsPrimaryDescIdAsc(userId);
    if (pets.isEmpty()) {
      loginUserStateService.updateCurrentPet(userId, null);
      return null;
    }
    PetResponse pet = toResponse(pets.get(0));
    loginUserStateService.updateCurrentPet(userId, pet);
    return pet;
  }

  @Transactional(readOnly = true)
  public List<PetResponse> listMyPets(Long userId) {
    return petRepository.findByUserIdOrderByIsPrimaryDescIdAsc(userId).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public PetResponse getMyPetById(Long userId, Long petId) {
    PetEntity pet = petRepository.findByIdAndUserId(petId, userId)
        .orElseThrow(() -> new BusinessException(ApiError.PET_NOT_FOUND, HttpStatus.NOT_FOUND));
    return toResponse(pet);
  }

  @Transactional
  public PetResponse createPet(Long userId, PetCreateRequest request) {
    PetCategoryEntity category = resolveCategory(request.categoryId());
    PetEntity pet = new PetEntity();
    pet.setUserId(userId);
    pet.setName(request.name());
    pet.setCategoryId(category == null ? null : category.getId());
    pet.setCategoryPath(category == null ? null : category.getPath());
    pet.setCustomSpeciesNote(normalizeText(request.customSpeciesNote()));
    pet.setBreed(buildDisplaySpecies(category, request.customSpeciesNote(), request.breed()));
    pet.setGender(normalizeGender(request.gender()));
    pet.setBirthDate(request.birthDate());
    pet.setNeutered(request.neutered());
    pet.setCurrentWeight(request.currentWeight());
    pet.setAvatarUrl(request.avatarUrl());
    pet.setTagsJson(toJson(request.tags()));
    pet.setIsPrimary(petRepository.countByUserId(userId) == 0);
    PetEntity saved = petRepository.save(pet);
    PetResponse response = toResponse(saved);
    if (response.primary()) {
      loginUserStateService.updateCurrentPet(userId, response);
    }
    return response;
  }

  @Transactional
  public void setPrimary(Long userId, Long petId) {
    PetEntity pet = petRepository.findByIdAndUserId(petId, userId)
        .orElseThrow(() -> new BusinessException(ApiError.PET_NOT_FOUND, HttpStatus.NOT_FOUND));
    petRepository.clearPrimary(userId);
    pet.setIsPrimary(true);
    petRepository.save(pet);
    loginUserStateService.updateCurrentPet(userId, toResponse(pet));
  }

  @Transactional
  public void updateAvatar(Long userId, Long petId, String avatarUrl) {
    PetEntity pet = petRepository.findByIdAndUserId(petId, userId)
        .orElseThrow(() -> new BusinessException(ApiError.PET_NOT_FOUND, HttpStatus.NOT_FOUND));
    pet.setAvatarUrl(avatarUrl);
    petRepository.save(pet);
    if (Boolean.TRUE.equals(pet.getIsPrimary())) {
      loginUserStateService.updateCurrentPet(userId, toResponse(pet));
    }
  }

  /**
   * 删除宠物时，除了主档案，还会同步清理它的体重记录，避免产生孤儿数据。
   * 如果删除的是当前主宠物，则自动把剩余列表中的第一只提升为新的主宠物。
   */
  @Transactional
  public void deletePet(Long userId, Long petId) {
    PetEntity pet = petRepository.findByIdAndUserId(petId, userId)
        .orElseThrow(() -> new BusinessException(ApiError.PET_NOT_FOUND, HttpStatus.NOT_FOUND));
    boolean deletingPrimary = Boolean.TRUE.equals(pet.getIsPrimary());
    petWeightRecordRepository.deleteByPetIdAndUserId(petId, userId);
    petRepository.delete(pet);

    if (!deletingPrimary) {
      return;
    }

    List<PetEntity> remainingPets = petRepository.findByUserIdOrderByIsPrimaryDescIdAsc(userId);
    if (remainingPets.isEmpty()) {
      loginUserStateService.updateCurrentPet(userId, null);
      return;
    }

    PetEntity nextPrimary = remainingPets.get(0);
    if (!Boolean.TRUE.equals(nextPrimary.getIsPrimary())) {
      petRepository.clearPrimary(userId);
      nextPrimary.setIsPrimary(true);
      petRepository.save(nextPrimary);
    }
    loginUserStateService.updateCurrentPet(userId, toResponse(nextPrimary));
  }

  @Transactional(readOnly = true)
  public void syncCurrentPrimaryToLoginState(Long userId) {
    List<PetEntity> pets = petRepository.findByUserIdOrderByIsPrimaryDescIdAsc(userId);
    if (pets.isEmpty()) {
      loginUserStateService.updateCurrentPet(userId, null);
      return;
    }
    loginUserStateService.updateCurrentPet(userId, toResponse(pets.get(0)));
  }

  private PetResponse toResponse(PetEntity pet) {
    PetCategoryEntity category = resolveCategoryQuietly(pet.getCategoryId());
    return new PetResponse(
        pet.getId(),
        pet.getName(),
        pet.getBreed(),
        pet.getCategoryId(),
        category == null ? null : category.getName(),
        pet.getCategoryPath(),
        pet.getCustomSpeciesNote(),
        pet.getGender(),
        pet.getBirthDate(),
        pet.getNeutered(),
        pet.getCurrentWeight(),
        pet.getAvatarUrl(),
        pet.getIsPrimary() != null && pet.getIsPrimary(),
        fromJson(pet.getTagsJson()));
  }

  private PetCategoryEntity resolveCategory(Long categoryId) {
    if (categoryId == null) {
      return null;
    }
    return petCategoryRepository.findByIdAndIsActiveTrue(categoryId)
        .orElseThrow(() -> new BusinessException(ApiError.VALIDATION_FAILED, HttpStatus.BAD_REQUEST));
  }

  private PetCategoryEntity resolveCategoryQuietly(Long categoryId) {
    if (categoryId == null) {
      return null;
    }
    return petCategoryRepository.findByIdAndIsActiveTrue(categoryId).orElse(null);
  }

  private String toJson(List<String> tags) {
    if (tags == null || tags.isEmpty()) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(tags);
    } catch (Exception ex) {
      throw new BusinessException(ApiError.VALIDATION_FAILED, HttpStatus.BAD_REQUEST);
    }
  }

  private List<String> fromJson(String tagsJson) {
    if (tagsJson == null || tagsJson.isBlank()) {
      return Collections.emptyList();
    }
    try {
      return objectMapper.readValue(tagsJson, new TypeReference<List<String>>() {});
    } catch (Exception ex) {
      return Collections.emptyList();
    }
  }

  private String normalizeText(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private String normalizeGender(String gender) {
    String normalized = normalizeText(gender);
    return normalized == null ? "unknown" : normalized;
  }

  /**
   * `breed` 继续保留为展示字段，结构化能力仍以 `categoryId/categoryPath/customSpeciesNote` 为准。
   */
  private String buildDisplaySpecies(PetCategoryEntity category, String customSpeciesNote, String legacyBreed) {
    String normalizedNote = normalizeText(customSpeciesNote);
    if (category != null) {
      return normalizedNote == null ? category.getName() : category.getName() + "（" + normalizedNote + "）";
    }
    String normalizedLegacyBreed = normalizeText(legacyBreed);
    return normalizedNote != null ? normalizedNote : normalizedLegacyBreed;
  }
}
