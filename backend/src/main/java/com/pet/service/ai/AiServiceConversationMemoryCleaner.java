package com.pet.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pet.config.AiProperties;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 使用 AIService 内部接口清理 Redis 短期记忆。
 *
 * <p>backend 不直接碰 AIService 自己的 Redis key，而是通过 AIService 暴露的内部接口删除。
 * 这样职责边界更清楚，后续即使 AIService 改了 key 规则，backend 也不用同步改实现细节。
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "aiservice")
public class AiServiceConversationMemoryCleaner implements AiConversationMemoryCleaner {
  private static final Logger aiLog = LoggerFactory.getLogger("com.pet.ai.interaction");

  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  public AiServiceConversationMemoryCleaner(
      RestClient.Builder restClientBuilder,
      AiProperties aiProperties,
      ObjectMapper objectMapper) {
    this.restClient = restClientBuilder
        .baseUrl(aiProperties.getAiService().getBaseUrl())
        .build();
    this.objectMapper = objectMapper;
  }

  @Override
  public void clearConversationMemory(String conversationId, Long userId) {
    try {
      aiLog.info(
          "backend->aiservice clear-memory conversationId={}, userId={}",
          conversationId,
          userId);

      Map<String, Object> response = restClient.delete()
          .uri(uriBuilder -> uriBuilder
              .path("/internal/ai/memory/{conversationId}")
              .queryParam("userId", userId)
              .build(conversationId))
          .accept(MediaType.APPLICATION_JSON)
          .retrieve()
          .body(Map.class);

      aiLog.info(
          "backend<-aiservice clear-memory conversationId={}, response={}",
          conversationId,
          toJson(response));
    } catch (RestClientException e) {
      aiLog.info(
          "backend<->aiservice clear-memory error conversationId={}, userId={}, message={}",
          conversationId,
          userId,
          e.getMessage());
      throw new IllegalStateException("Failed to clear AIService memory: " + e.getMessage(), e);
    }
  }

  private String toJson(Object value) {
    if (value == null) {
      return "null";
    }
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      return String.valueOf(value);
    }
  }
}
