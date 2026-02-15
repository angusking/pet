package com.pet.api;

import com.pet.api.dto.EchoRequest;
import com.pet.api.dto.TokenRequest;
import com.pet.api.dto.TokenResponse;
import com.pet.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
public class PublicController {
  private final JwtService jwtService;

  public PublicController(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  @PostMapping("/echo")
  public EchoRequest echo(@Valid @RequestBody EchoRequest request) {
    return request;
  }

  @PostMapping("/token")
  public TokenResponse token(@Valid @RequestBody TokenRequest request) {
    String token = jwtService.generateToken(0L, "USER");
    return new TokenResponse(token);
  }
}
