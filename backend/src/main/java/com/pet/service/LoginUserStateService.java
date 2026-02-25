package com.pet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pet.api.pet.dto.PetResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class LoginUserStateService {
  private static final Logger log = LoggerFactory.getLogger(LoginUserStateService.class);
  private static final String KEY_PREFIX = "login:user:state:";
  private static final Duration TTL = Duration.ofDays(7);

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  public LoginUserStateService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
  }

  public PetResponse getCurrentPet(Long userId) {
    try {
      String raw = redisTemplate.opsForValue().get(key(userId));
      if (raw == null || raw.isBlank()) {
        return null;
      }
      LoginUserState state = objectMapper.readValue(raw, LoginUserState.class);
      return state.currentPet;
    } catch (DataAccessException ex) {
      log.warn("Redis unavailable when reading login user state, userId={}", userId);
      return null;
    } catch (Exception ex) {
      log.warn("Failed to parse login user state, userId={}", userId);
      return null;
    }
  }

  public void updateCurrentPet(Long userId, PetResponse currentPet) {
    try {
      LoginUserState state = new LoginUserState();
      state.userId = userId;
      state.currentPet = currentPet;
      state.updatedAt = LocalDateTime.now();
      redisTemplate.opsForValue().set(key(userId), objectMapper.writeValueAsString(state), TTL);
    } catch (DataAccessException ex) {
      log.warn("Redis unavailable when updating login user state, userId={}", userId);
    } catch (Exception ex) {
      log.warn("Failed to write login user state, userId={}", userId);
    }
  }

  private String key(Long userId) {
    return KEY_PREFIX + userId;
  }

  public static class LoginUserState {
    public Long userId;
    public PetResponse currentPet;
    public LocalDateTime updatedAt;
  }
}
