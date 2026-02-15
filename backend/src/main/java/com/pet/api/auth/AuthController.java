package com.pet.api.auth;

import com.pet.api.auth.dto.AuthResponse;
import com.pet.api.auth.dto.LoginRequest;
import com.pet.api.auth.dto.RegisterRequest;
import com.pet.security.JwtService;
import com.pet.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthService authService;
  private final JwtService jwtService;

  public AuthController(AuthService authService, JwtService jwtService) {
    this.authService = authService;
    this.jwtService = jwtService;
  }

  @PostMapping("/register")
  public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
    String token = authService.register(request);
    return new AuthResponse(token, jwtService.getExpirationSeconds());
  }

  @PostMapping("/login")
  public AuthResponse login(@Valid @RequestBody LoginRequest request) {
    String token = authService.login(request);
    return new AuthResponse(token, jwtService.getExpirationSeconds());
  }
}
