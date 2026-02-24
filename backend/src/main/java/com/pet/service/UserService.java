package com.pet.service;

import com.pet.api.error.ApiError;
import com.pet.api.error.BusinessException;
import com.pet.api.user.dto.MeProfileResponse;
import com.pet.entity.UserEntity;
import com.pet.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
  private final UserRepository userRepository;

  public UserService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Transactional(readOnly = true)
  public MeProfileResponse getProfile(Long userId) {
    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> new BusinessException(ApiError.USER_NOT_FOUND, HttpStatus.NOT_FOUND));
    return new MeProfileResponse(user.getId(), user.getUsername(), user.getNickname(), user.getAvatarUrl());
  }

  @Transactional
  public void updateAvatar(Long userId, String avatarUrl) {
    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> new BusinessException(ApiError.USER_NOT_FOUND, HttpStatus.NOT_FOUND));
    user.setAvatarUrl(avatarUrl);
    userRepository.save(user);
  }
}
