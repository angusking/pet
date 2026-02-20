package com.pet.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pet.api.error.ApiError;
import com.pet.api.error.BusinessException;
import com.pet.entity.UserEntity;
import com.pet.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
  @Mock
  private UserRepository userRepository;

  @InjectMocks
  private UserService userService;

  @Test
  void updateAvatarShouldThrowWhenUserNotFound() {
    when(userRepository.findById(1L)).thenReturn(Optional.empty());

    BusinessException ex = assertThrows(BusinessException.class,
        () -> userService.updateAvatar(1L, "/uploads/new.png"));

    assertEquals(ApiError.USER_NOT_FOUND, ex.getError());
    assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
  }

  @Test
  void updateAvatarShouldUpdateAndSaveWhenUserExists() {
    UserEntity user = new UserEntity();
    user.setId(1L);
    user.setAvatarUrl("/old.png");
    when(userRepository.findById(1L)).thenReturn(Optional.of(user));

    userService.updateAvatar(1L, "/uploads/new.png");

    assertEquals("/uploads/new.png", user.getAvatarUrl());
    verify(userRepository).save(user);
  }
}
