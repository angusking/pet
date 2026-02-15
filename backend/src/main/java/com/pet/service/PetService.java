package com.pet.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pet.api.error.ApiError;
import com.pet.api.error.BusinessException;
import com.pet.api.pet.dto.PetCreateRequest;
import com.pet.api.pet.dto.PetResponse;
import com.pet.entity.PetEntity;
import com.pet.repository.PetRepository;
import java.util.Collections;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PetService {
  private final PetRepository petRepository;
  private final ObjectMapper objectMapper;

  public PetService(PetRepository petRepository, ObjectMapper objectMapper) {
    this.petRepository = petRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional(readOnly = true)
  public PetResponse getMyPrimaryPet(Long userId) {
    List<PetEntity> pets = petRepository.findByUserIdOrderByIsPrimaryDescIdAsc(userId);
    if (pets.isEmpty()) {
      return null;
    }
    return toResponse(pets.get(0));
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
    PetEntity pet = new PetEntity();
    pet.setUserId(userId);
    pet.setName(request.name());
    pet.setBreed(request.breed());
    pet.setGender(request.gender());
    pet.setBirthday(request.birthday());
    pet.setWeightKg(request.weightKg());
    pet.setAvatarUrl(request.avatarUrl());
    pet.setTagsJson(toJson(request.tags()));
    pet.setIsPrimary(petRepository.countByUserId(userId) == 0);
    PetEntity saved = petRepository.save(pet);
    return toResponse(saved);
  }

  @Transactional
  public void setPrimary(Long userId, Long petId) {
    PetEntity pet = petRepository.findByIdAndUserId(petId, userId)
        .orElseThrow(() -> new BusinessException(ApiError.PET_NOT_FOUND, HttpStatus.NOT_FOUND));
    petRepository.clearPrimary(userId);
    pet.setIsPrimary(true);
    petRepository.save(pet);
  }

  @Transactional
  public void updateAvatar(Long userId, Long petId, String avatarUrl) {
    PetEntity pet = petRepository.findByIdAndUserId(petId, userId)
        .orElseThrow(() -> new BusinessException(ApiError.PET_NOT_FOUND, HttpStatus.NOT_FOUND));
    pet.setAvatarUrl(avatarUrl);
    petRepository.save(pet);
  }

  private PetResponse toResponse(PetEntity pet) {
    return new PetResponse(
        pet.getId(),
        pet.getName(),
        pet.getBreed(),
        pet.getGender(),
        pet.getBirthday(),
        pet.getWeightKg(),
        pet.getAvatarUrl(),
        pet.getIsPrimary() != null && pet.getIsPrimary(),
        fromJson(pet.getTagsJson())
    );
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
}
