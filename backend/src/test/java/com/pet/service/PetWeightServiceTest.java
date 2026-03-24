package com.pet.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pet.api.error.ApiError;
import com.pet.api.error.BusinessException;
import com.pet.api.pet.dto.PetWeightRecordCreateRequest;
import com.pet.api.pet.dto.PetWeightRecordListResponse;
import com.pet.api.pet.dto.PetWeightRecordResponse;
import com.pet.entity.PetEntity;
import com.pet.entity.PetWeightRecordEntity;
import com.pet.repository.PetRepository;
import com.pet.repository.PetWeightRecordRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class PetWeightServiceTest {
  @Mock
  private PetRepository petRepository;
  @Mock
  private PetWeightRecordRepository petWeightRecordRepository;
  @Mock
  private PetService petService;

  @InjectMocks
  private PetWeightService petWeightService;

  @Test
  void listRecordsShouldReturnSummaryAndRecords() {
    PetEntity pet = pet(8L, 1L, true, "dog/dog_small/corgi");
    PetWeightRecordEntity latest = record(101L, 8L, 1L, "12.20", LocalDateTime.of(2026, 3, 20, 9, 0));
    PetWeightRecordEntity previous = record(100L, 8L, 1L, "11.80", LocalDateTime.of(2026, 3, 1, 9, 0));
    when(petRepository.findByIdAndUserId(8L, 1L)).thenReturn(Optional.of(pet));
    when(petWeightRecordRepository.findByPetIdAndUserIdOrderByRecordedAtDescIdDesc(8L, 1L))
        .thenReturn(List.of(latest, previous));

    PetWeightRecordListResponse response = petWeightService.listRecords(1L, 8L);

    assertEquals(new BigDecimal("12.20"), response.summary().currentWeight());
    assertEquals(new BigDecimal("0.40"), response.summary().changeFromPrevious());
    assertEquals("up", response.summary().trendDirection());
    assertEquals("precise", response.summary().categorySupportLevel());
    assertEquals(2, response.records().size());
    assertEquals(new BigDecimal("0.40"), response.records().get(0).changeFromPrevious());
    assertNull(response.records().get(1).changeFromPrevious());
  }

  @Test
  void createRecordShouldSaveAndRefreshCurrentWeight() {
    PetEntity pet = pet(8L, 1L, true, "cat/cat_short/british_shorthair");
    PetWeightRecordCreateRequest request = new PetWeightRecordCreateRequest(
        new BigDecimal("4.236"),
        "kg",
        "home",
        "before dinner",
        LocalDateTime.of(2026, 3, 23, 20, 0));
    PetWeightRecordEntity saved = record(201L, 8L, 1L, "4.24", LocalDateTime.of(2026, 3, 23, 20, 0));
    when(petRepository.findByIdAndUserId(8L, 1L)).thenReturn(Optional.of(pet));
    when(petWeightRecordRepository.save(any(PetWeightRecordEntity.class))).thenReturn(saved);
    when(petWeightRecordRepository.findFirstByPetIdAndUserIdAndRecordedAtBeforeOrderByRecordedAtDescIdDesc(
        8L, 1L, LocalDateTime.of(2026, 3, 23, 20, 0))).thenReturn(Optional.empty());
    when(petWeightRecordRepository.findFirstByPetIdAndUserIdOrderByRecordedAtDescIdDesc(8L, 1L))
        .thenReturn(Optional.of(saved));

    PetWeightRecordResponse response = petWeightService.createRecord(1L, 8L, request);

    assertEquals(new BigDecimal("4.24"), response.weightValue());
    assertEquals("kg", response.unit());
    verify(petRepository).save(pet);
    assertEquals(new BigDecimal("4.24"), pet.getCurrentWeight());
    verify(petService).syncCurrentPrimaryToLoginState(1L);
  }

  @Test
  void deleteRecordShouldThrowWhenRecordNotFound() {
    PetEntity pet = pet(8L, 1L, false, "bird/parrot/cockatiel");
    when(petRepository.findByIdAndUserId(8L, 1L)).thenReturn(Optional.of(pet));
    when(petWeightRecordRepository.findByIdAndPetIdAndUserId(300L, 8L, 1L)).thenReturn(Optional.empty());

    BusinessException ex = assertThrows(BusinessException.class,
        () -> petWeightService.deleteRecord(1L, 8L, 300L));

    assertEquals(ApiError.PET_WEIGHT_RECORD_NOT_FOUND, ex.getError());
    assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    verify(petWeightRecordRepository, never()).delete(any());
  }

  private static PetEntity pet(Long id, Long userId, boolean primary, String categoryPath) {
    PetEntity pet = new PetEntity();
    pet.setId(id);
    pet.setUserId(userId);
    pet.setName("Milo");
    pet.setBreed("demo breed");
    pet.setGender("male");
    pet.setBirthDate(LocalDate.of(2025, 1, 1));
    pet.setIsPrimary(primary);
    pet.setCategoryPath(categoryPath);
    return pet;
  }

  private static PetWeightRecordEntity record(
      Long id,
      Long petId,
      Long userId,
      String weight,
      LocalDateTime recordedAt) {
    PetWeightRecordEntity record = new PetWeightRecordEntity();
    record.setId(id);
    record.setPetId(petId);
    record.setUserId(userId);
    record.setWeightValue(new BigDecimal(weight));
    record.setUnit("kg");
    record.setRecordedAt(recordedAt);
    return record;
  }
}
