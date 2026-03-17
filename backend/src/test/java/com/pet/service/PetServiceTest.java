package com.pet.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class PetServiceTest {
  @Mock
  private PetRepository petRepository;
  @Mock
  private PetCategoryRepository petCategoryRepository;
  @Mock
  private ObjectMapper objectMapper;
  @Mock
  private LoginUserStateService loginUserStateService;

  @InjectMocks
  private PetService petService;

  @Test
  void getMyPrimaryPetShouldReturnNullWhenEmpty() {
    when(petRepository.findByUserIdOrderByIsPrimaryDescIdAsc(1L)).thenReturn(List.of());

    PetResponse res = petService.getMyPrimaryPet(1L);

    assertNull(res);
  }

  @Test
  void getMyPrimaryPetShouldReturnFirstPet() {
    PetEntity p1 = pet(10L, 1L, "Milo", true);
    PetEntity p2 = pet(11L, 1L, "Other", false);
    when(petRepository.findByUserIdOrderByIsPrimaryDescIdAsc(1L)).thenReturn(List.of(p1, p2));

    PetResponse res = petService.getMyPrimaryPet(1L);

    assertNotNull(res);
    assertEquals(10L, res.id());
    assertEquals("Milo", res.name());
    assertTrue(res.primary());
  }

  @Test
  void listMyPetsShouldMapAllFields() {
    PetEntity p1 = pet(10L, 1L, "Milo", true);
    p1.setCurrentWeight(new BigDecimal("12.50"));
    PetEntity p2 = pet(11L, 1L, "Nana", false);
    when(petRepository.findByUserIdOrderByIsPrimaryDescIdAsc(1L)).thenReturn(List.of(p1, p2));

    List<PetResponse> list = petService.listMyPets(1L);

    assertEquals(2, list.size());
    assertEquals(new BigDecimal("12.50"), list.get(0).currentWeight());
    assertFalse(list.get(1).primary());
  }

  @Test
  void getMyPetByIdShouldThrowWhenNotFound() {
    when(petRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

    BusinessException ex = assertThrows(BusinessException.class,
        () -> petService.getMyPetById(1L, 99L));

    assertEquals(ApiError.PET_NOT_FOUND, ex.getError());
    assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
  }

  @Test
  void createPetShouldSetPrimaryTrueWhenFirstPet() {
    PetCategoryEntity category = category(26L, "玄凤鹦鹉", "bird/parrot/cockatiel");
    PetCreateRequest req = new PetCreateRequest(
        "Milo", 26L, "黄化大体", null, "female", LocalDate.of(2024, 1, 1), true, new BigDecimal("12.5"), "/uploads/a.png", null);

    when(petCategoryRepository.findByIdAndIsActiveTrue(26L)).thenReturn(Optional.of(category));
    when(petRepository.countByUserId(1L)).thenReturn(0L);
    when(petRepository.save(any(PetEntity.class))).thenAnswer(invocation -> {
      PetEntity e = invocation.getArgument(0);
      e.setId(100L);
      return e;
    });

    PetResponse res = petService.createPet(1L, req);

    assertEquals(100L, res.id());
    assertTrue(res.primary());
    assertEquals(new BigDecimal("12.5"), res.currentWeight());
    assertEquals("bird/parrot/cockatiel", res.categoryPath());
    assertEquals("玄凤鹦鹉（黄化大体）", res.breed());

    ArgumentCaptor<PetEntity> captor = ArgumentCaptor.forClass(PetEntity.class);
    verify(petRepository).save(captor.capture());
    assertEquals(Boolean.TRUE, captor.getValue().getIsPrimary());
    assertEquals(26L, captor.getValue().getCategoryId());
    assertEquals("bird/parrot/cockatiel", captor.getValue().getCategoryPath());
  }

  @Test
  void createPetShouldSetPrimaryFalseWhenNotFirstPet() {
    PetCreateRequest req = new PetCreateRequest(
        "Milo", null, null, "Golden", "male", LocalDate.of(2024, 1, 1), null, null, "/uploads/a.png", null);

    when(petRepository.countByUserId(1L)).thenReturn(2L);
    when(petRepository.save(any(PetEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

    PetResponse res = petService.createPet(1L, req);

    assertFalse(res.primary());
    assertEquals("Golden", res.breed());
  }

  @Test
  void createPetShouldThrowWhenCategoryNotFound() {
    PetCreateRequest req = new PetCreateRequest(
        "Milo", 999L, null, null, "unknown", null, null, null, "/uploads/a.png", null);
    when(petCategoryRepository.findByIdAndIsActiveTrue(999L)).thenReturn(Optional.empty());

    BusinessException ex = assertThrows(BusinessException.class,
        () -> petService.createPet(1L, req));

    assertEquals(ApiError.VALIDATION_FAILED, ex.getError());
    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
  }

  @Test
  void createPetShouldThrowValidationFailedWhenTagsJsonError() throws Exception {
    PetCreateRequest req = new PetCreateRequest(
        "Milo", null, null, "Golden", "unknown", null, null, null, "/uploads/a.png", List.of("健康"));

    when(objectMapper.writeValueAsString(any())).thenThrow(new RuntimeException("json error"));

    BusinessException ex = assertThrows(BusinessException.class,
        () -> petService.createPet(1L, req));

    assertEquals(ApiError.VALIDATION_FAILED, ex.getError());
    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    verify(petRepository, never()).save(any(PetEntity.class));
  }

  @Test
  void setPrimaryShouldThrowWhenPetNotFound() {
    when(petRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.empty());

    BusinessException ex = assertThrows(BusinessException.class,
        () -> petService.setPrimary(1L, 10L));

    assertEquals(ApiError.PET_NOT_FOUND, ex.getError());
  }

  @Test
  void setPrimaryShouldClearOldAndSaveNewPrimary() {
    PetEntity pet = pet(10L, 1L, "Milo", false);
    when(petRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(pet));

    petService.setPrimary(1L, 10L);

    verify(petRepository).clearPrimary(1L);
    verify(petRepository).save(pet);
    assertEquals(Boolean.TRUE, pet.getIsPrimary());
  }

  @Test
  void updateAvatarShouldThrowWhenPetNotFound() {
    when(petRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.empty());

    BusinessException ex = assertThrows(BusinessException.class,
        () -> petService.updateAvatar(1L, 10L, "/uploads/new.png"));

    assertEquals(ApiError.PET_NOT_FOUND, ex.getError());
  }

  @Test
  void updateAvatarShouldUpdateAndSave() {
    PetEntity pet = pet(10L, 1L, "Milo", true);
    when(petRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(pet));

    petService.updateAvatar(1L, 10L, "/uploads/new.png");

    assertEquals("/uploads/new.png", pet.getAvatarUrl());
    verify(petRepository).save(pet);
  }

  @Test
  void deletePetShouldThrowWhenPetNotFound() {
    when(petRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.empty());

    BusinessException ex = assertThrows(BusinessException.class,
        () -> petService.deletePet(1L, 10L));

    assertEquals(ApiError.PET_NOT_FOUND, ex.getError());
    assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
  }

  @Test
  void deletePetShouldDeleteNonPrimaryWithoutChangingLoginState() {
    PetEntity pet = pet(10L, 1L, "Milo", false);
    when(petRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(pet));

    petService.deletePet(1L, 10L);

    verify(petRepository).delete(pet);
    verify(loginUserStateService, never()).updateCurrentPet(any(), any());
  }

  @Test
  void deletePetShouldPromoteNextPrimaryWhenDeletingCurrentPrimary() {
    PetEntity current = pet(10L, 1L, "Milo", true);
    PetEntity next = pet(11L, 1L, "Nana", false);
    when(petRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(current));
    when(petRepository.findByUserIdOrderByIsPrimaryDescIdAsc(1L)).thenReturn(List.of(next));

    petService.deletePet(1L, 10L);

    verify(petRepository).delete(current);
    verify(petRepository).clearPrimary(1L);
    verify(petRepository).save(next);
    assertTrue(next.getIsPrimary());
    verify(loginUserStateService, times(1)).updateCurrentPet(any(), any());
  }

  @Test
  void deletePetShouldClearCurrentPetWhenNoRemainingPets() {
    PetEntity current = pet(10L, 1L, "Milo", true);
    when(petRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(current));
    when(petRepository.findByUserIdOrderByIsPrimaryDescIdAsc(1L)).thenReturn(List.of());

    petService.deletePet(1L, 10L);

    verify(petRepository).delete(current);
    verify(loginUserStateService).updateCurrentPet(1L, null);
  }

  @SuppressWarnings("unchecked")
  @Test
  void listMyPetsShouldParseTagsWhenJsonValid() throws Exception {
    PetEntity p1 = pet(10L, 1L, "Milo", true);
    p1.setTagsJson("[\"健康正常\"]");
    when(petRepository.findByUserIdOrderByIsPrimaryDescIdAsc(1L)).thenReturn(List.of(p1));
    when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(List.of("健康正常"));

    List<PetResponse> list = petService.listMyPets(1L);

    assertEquals(1, list.size());
    assertEquals(List.of("健康正常"), list.get(0).tags());
  }

  private static PetEntity pet(Long id, Long userId, String name, boolean primary) {
    PetEntity pet = new PetEntity();
    pet.setId(id);
    pet.setUserId(userId);
    pet.setName(name);
    pet.setBreed("Golden");
    pet.setGender("male");
    pet.setBirthDate(LocalDate.of(2024, 1, 1));
    pet.setAvatarUrl("/uploads/a.png");
    pet.setIsPrimary(primary);
    return pet;
  }

  private static PetCategoryEntity category(Long id, String name, String path) {
    PetCategoryEntity category = new PetCategoryEntity();
    category.setId(id);
    category.setName(name);
    category.setPath(path);
    return category;
  }
}
