package com.pet.service;

import com.pet.api.error.ApiError;
import com.pet.api.error.BusinessException;
import com.pet.api.auth.dto.LoginRequest;
import com.pet.api.auth.dto.RegisterRequest;
import com.pet.entity.UserEntity;
import com.pet.repository.UserRepository;
import com.pet.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final PetService petService;

  public AuthService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      PetService petService) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.petService = petService;
  }

  public String register(RegisterRequest request) {
    if (userRepository.existsByUsername(request.username())) {
      throw new BusinessException(ApiError.USERNAME_EXISTS, HttpStatus.CONFLICT);
    }
    UserEntity user = new UserEntity();
    user.setUsername(request.username());
    user.setPasswordHash(passwordEncoder.encode(request.password()));
    user.setNickname(request.nickname());
    user.setRole("USER");
    user.setStatus("ACTIVE");
    UserEntity saved = userRepository.save(user);
    petService.syncCurrentPrimaryToLoginState(saved.getId());
    return jwtService.generateToken(saved.getId(), saved.getRole());
  }

  public String login(LoginRequest request) {
    UserEntity user = userRepository.findByUsername(request.username())
        .orElseThrow(() -> new BusinessException(ApiError.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED));
    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw new BusinessException(ApiError.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED);
    }
    petService.syncCurrentPrimaryToLoginState(user.getId());
    return jwtService.generateToken(user.getId(), user.getRole());
  }
}
