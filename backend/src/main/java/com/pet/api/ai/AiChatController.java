package com.pet.api.ai;

import com.pet.api.ai.dto.AiChatCreateRequest;
import com.pet.api.ai.dto.AiChatMessageResponse;
import com.pet.api.ai.dto.AiChatSendMessageRequest;
import com.pet.api.ai.dto.AiChatSendMessageResponse;
import com.pet.api.ai.dto.AiChatSessionResponse;
import com.pet.api.ai.dto.AiChatUpdatePetRequest;
import com.pet.service.AiChatService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/chats")
public class AiChatController {
  private final AiChatService aiChatService;

  public AiChatController(AiChatService aiChatService) {
    this.aiChatService = aiChatService;
  }

  @GetMapping
  public List<AiChatSessionResponse> listSessions(Authentication authentication) {
    Long userId = (Long) authentication.getPrincipal();
    return aiChatService.listSessions(userId);
  }

  @PostMapping
  public AiChatSessionResponse createSession(
      @RequestBody(required = false) AiChatCreateRequest request,
      Authentication authentication) {
    Long userId = (Long) authentication.getPrincipal();
    return aiChatService.createSession(userId, request);
  }

  @GetMapping("/{sessionId}/messages")
  public List<AiChatMessageResponse> listMessages(
      @PathVariable("sessionId") Long sessionId,
      Authentication authentication) {
    Long userId = (Long) authentication.getPrincipal();
    return aiChatService.listMessages(userId, sessionId);
  }

  @PostMapping("/{sessionId}/pet")
  public AiChatSessionResponse updateSessionPet(
      @PathVariable("sessionId") Long sessionId,
      @RequestBody(required = false) AiChatUpdatePetRequest request,
      Authentication authentication) {
    Long userId = (Long) authentication.getPrincipal();
    return aiChatService.updateSessionPet(userId, sessionId, request);
  }

  @PostMapping("/{sessionId}/messages")
  public AiChatSendMessageResponse sendMessage(
      @PathVariable("sessionId") Long sessionId,
      @Valid @RequestBody AiChatSendMessageRequest request,
      Authentication authentication) {
    Long userId = (Long) authentication.getPrincipal();
    return aiChatService.sendMessage(userId, sessionId, request);
  }
}
