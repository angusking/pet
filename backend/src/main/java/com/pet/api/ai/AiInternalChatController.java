package com.pet.api.ai;

import com.pet.api.ai.dto.AiRecentChatMessageResponse;
import com.pet.service.AiChatService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 这是给 AIService 使用的内部接口，不直接面向前端。
 *
 * <p>当前阶段里，AIService 仍然优先依赖 Redis 短期记忆。
 * 只有在 Redis miss 时，才会通过这个接口向 backend 回源最近几条消息，用来恢复上下文。
 */
@RestController
@RequestMapping("/internal/ai/chats")
public class AiInternalChatController {
  private final AiChatService aiChatService;

  public AiInternalChatController(AiChatService aiChatService) {
    this.aiChatService = aiChatService;
  }

  @GetMapping("/{sessionId}/recent-messages")
  public List<AiRecentChatMessageResponse> listRecentMessages(
      @PathVariable("sessionId") Long sessionId,
      @RequestParam("userId") Long userId,
      @RequestParam(name = "limit", defaultValue = "8") Integer limit) {
    return aiChatService.listRecentMessages(userId, sessionId, limit);
  }
}
