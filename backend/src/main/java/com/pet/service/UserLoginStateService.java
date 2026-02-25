package com.pet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pet.api.pet.dto.PetResponse;
import com.pet.entity.PetEntity;
import com.pet.repository.PetRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class UserLoginStateService {
  private static final Logger log = LoggerFactory.getLogger(UserLoginStateService.class);
  private static final Duration STATE_TTL = Duration.ofDays(30);
  private static final String KEY_PREFIX = "pet:user:login-state:";

  private final StringRedisTemplate stringRedisTemplate;
  private final PetRepository petRepository;
  private final ObjectMapper objectMapper;

  public UserLoginStateService(StringRedisTemplate stringRedisTemplate, PetRepository petRepository, ObjectMapper objectMapper) {
    this.stringRedisTemplate = stringRedisTemplate;
    this.petRepository = petRepository;
    this.objectMapper = objectMapper;
  }

  public void initializeState(Long userId) {
    if (userId == null) {
      return;
    }
    PetResponse currentPet = loadPrimaryPet(userId);
    saveState(userId, currentPet);
  }

  public void updateCurrentPet(Long userId, PetResponse pet) {
    if (userId == null) {
      return;
    }
    saveState(userId, pet);
  }

  public void refreshCurrentPetIfMatches(Long userId, PetResponse pet) {
    if (userId == null || pet == null || pet.id() == null) {
      return;
    }
    LoginUserState state = getState(userId);
    if (state == null || state.currentPetId() == null || !state.currentPetId().equals(pet.id())) {
      return;
    }
    saveState(userId, pet);
  }

  public Long getCurrentPetId(Long userId) {
    LoginUserState state = getState(userId);
    return state == null ? null : state.currentPetId();
  }

  public LoginUserState getState(Long userId) {
    try {
      String json = stringRedisTemplate.opsForValue().get(key(userId));
      if (json == null || json.isBlank()) {
        return null;
      }
      return objectMapper.readValue(json, LoginUserState.class);
    } catch (DataAccessException e) {
      log.warn("Redis unavailable when reading login state for user {}: {}", userId, e.getMessage());
      return null;
    } catch (Exception e) {
      log.warn("Failed to parse login state for user {}: {}", userId, e.getMessage());
      return null;
    }
  }

  private void saveState(Long userId, PetResponse pet) {
    LoginUserState state = new LoginUserState(
        userId,
        pet == null ? null : pet.id(),
        pet == null ? null : pet.name(),
        pet == null ? null : pet.breed(),
        pet == null ? null : pet.avatarUrl(),
        LocalDateTime.now()
    );
    try {
      String json = objectMapper.writeValueAsString(state);
      stringRedisTemplate.opsForValue().set(key(userId), json, STATE_TTL);
    } catch (DataAccessException e) {
      log.warn("Redis unavailable when writing login state for user {}: {}", userId, e.getMessage());
    } catch (Exception e) {
      log.warn("Failed to serialize login state for user {}: {}", userId, e.getMessage());
    }
  }

  private PetResponse loadPrimaryPet(Long userId) {
    List<PetEntity> pets = petRepository.findByUserIdOrderByIsPrimaryDescIdAsc(userId);
    if (pets.isEmpty()) {
      return null;
    }
    PetEntity pet = pets.get(0);
    return new PetResponse(
        pet.getId(),
        pet.getName(),
        pet.getBreed(),
        pet.getGender(),
        pet.getBirthday(),
        pet.getWeightKg(),
        pet.getAvatarUrl(),
        pet.getIsPrimary() != null && pet.getIsPrimary(),
        List.of()
    );
  }

  private String key(Long userId) {
    return KEY_PREFIX + userId;
  }

  public record LoginUserState(
      Long userId,
      Long currentPetId,
      String currentPetName,
      String currentPetBreed,
      String currentPetAvatarUrl,
      LocalDateTime updatedAt
  ) {}
}
