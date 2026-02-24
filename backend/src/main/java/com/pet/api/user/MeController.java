package com.pet.api.user;

import com.pet.api.user.dto.AvatarUpdateRequest;
import com.pet.api.user.dto.MeProfileResponse;
import com.pet.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class MeController {
  private final UserService userService;

  public MeController(UserService userService) {
    this.userService = userService;
  }

  @GetMapping("/profile")
  public MeProfileResponse profile(Authentication authentication) {
    Long userId = (Long) authentication.getPrincipal();
    return userService.getProfile(userId);
  }

  @PostMapping("/avatar")
  public void updateAvatar(@Valid @RequestBody AvatarUpdateRequest request, Authentication authentication) {
    Long userId = (Long) authentication.getPrincipal();
    userService.updateAvatar(userId, request.avatarUrl());
  }
}
