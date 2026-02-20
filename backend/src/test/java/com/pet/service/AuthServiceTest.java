package com.pet.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pet.api.auth.dto.LoginRequest;
import com.pet.api.auth.dto.RegisterRequest;
import com.pet.api.error.ApiError;
import com.pet.api.error.BusinessException;
import com.pet.entity.UserEntity;
import com.pet.repository.UserRepository;
import com.pet.security.JwtService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
  @Mock
  private UserRepository userRepository;
  @Mock
  private PasswordEncoder passwordEncoder;
  @Mock
  private JwtService jwtService;

  @InjectMocks
  private AuthService authService;

  @Test
  void registerShouldThrowWhenUsernameExists() {
    when(userRepository.existsByUsername("alice")).thenReturn(true);

    BusinessException ex = assertThrows(BusinessException.class,
        () -> authService.register(new RegisterRequest("alice", "Password1!", "Alice")));

    assertEquals(ApiError.USERNAME_EXISTS, ex.getError());
    assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    verify(userRepository, never()).save(any(UserEntity.class));
  }

  @Test
  void registerShouldCreateUserAndReturnToken() {
    when(userRepository.existsByUsername("alice")).thenReturn(false);
    when(passwordEncoder.encode("Password1!")).thenReturn("hashed");

    UserEntity saved = new UserEntity();
    saved.setId(1L);
    saved.setRole("USER");
    when(userRepository.save(any(UserEntity.class))).thenReturn(saved);
    when(jwtService.generateToken(1L, "USER")).thenReturn("token-1");

    String token = authService.register(new RegisterRequest("alice", "Password1!", "Alice"));

    assertEquals("token-1", token);
    ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
    verify(userRepository).save(captor.capture());
    UserEntity toSave = captor.getValue();
    assertEquals("alice", toSave.getUsername());
    assertEquals("hashed", toSave.getPasswordHash());
    assertEquals("Alice", toSave.getNickname());
    assertEquals("USER", toSave.getRole());
    assertEquals("ACTIVE", toSave.getStatus());
    verify(jwtService).generateToken(1L, "USER");
  }

  @Test
  void loginShouldThrowWhenUserNotFound() {
    when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());

    BusinessException ex = assertThrows(BusinessException.class,
        () -> authService.login(new LoginRequest("alice", "Password1!")));

    assertEquals(ApiError.INVALID_CREDENTIALS, ex.getError());
    assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
  }

  @Test
  void loginShouldThrowWhenPasswordNotMatch() {
    UserEntity user = new UserEntity();
    user.setPasswordHash("hashed");
    when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

    BusinessException ex = assertThrows(BusinessException.class,
        () -> authService.login(new LoginRequest("alice", "wrong")));

    assertEquals(ApiError.INVALID_CREDENTIALS, ex.getError());
    assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    verify(jwtService, never()).generateToken(any(), any());
  }

  @Test
  void loginShouldReturnTokenWhenSuccess() {
    UserEntity user = new UserEntity();
    user.setId(2L);
    user.setRole("USER");
    user.setPasswordHash("hashed");

    when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("Password1!", "hashed")).thenReturn(true);
    when(jwtService.generateToken(2L, "USER")).thenReturn("token-2");

    String token = authService.login(new LoginRequest("alice", "Password1!"));

    assertTrue(token.startsWith("token-"));
    assertEquals("token-2", token);
    verify(jwtService).generateToken(eq(2L), eq("USER"));
  }
}
