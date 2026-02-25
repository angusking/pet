package com.pet.service.ai;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.protocol.Protocol;
import com.pet.config.AiProperties;
import com.pet.entity.PetEntity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "qwen")
public class QwenDashScopeProvider implements AiProvider {
  private static final Logger log = LoggerFactory.getLogger(QwenDashScopeProvider.class);

  private final AiProperties aiProperties;

  public QwenDashScopeProvider(AiProperties aiProperties) {
    this.aiProperties = aiProperties;
  }

  @Override
  public AiReply generateReply(Long userId, PetEntity pet, String userMessage) {
    try {
      GenerationResult result = createGeneration().call(buildParam(pet, userMessage));
      String content = extractContent(result);
      Integer tokens = extractTokens(result);
      String requestId = extractRequestId(result);
      return new AiReply(
          content == null || content.isBlank() ? "AI 未返回有效内容，请稍后重试。" : content,
          aiProperties.getQwen().getModel(),
          tokens,
          requestId);
    } catch (NoApiKeyException | ApiException | InputRequiredException e) {
      throw new IllegalStateException("调用千问失败: " + e.getMessage(), e);
    }
  }

  private Generation createGeneration() {
    String baseUrl = trimToNull(aiProperties.getQwen().getBaseUrl());
    if (baseUrl == null) {
      return new Generation();
    }
    return new Generation(Protocol.HTTP.getValue(), baseUrl);
  }

  private GenerationParam buildParam(PetEntity pet, String userMessage) {
    String apiKey = trimToNull(aiProperties.getQwen().getApiKey());
    List<Message> messages = new ArrayList<>();
    messages.add(Message.builder()
        .role(Role.SYSTEM.getValue())
        .content(buildSystemPrompt(pet))
        .build());
    messages.add(Message.builder()
        .role(Role.USER.getValue())
        .content(userMessage)
        .build());

    var builder = GenerationParam.builder()
        .model(aiProperties.getQwen().getModel())
        .messages(messages)
        .resultFormat(GenerationParam.ResultFormat.MESSAGE);

    if (aiProperties.getQwen().getMaxTokens() != null) {
      builder.maxTokens(aiProperties.getQwen().getMaxTokens());
    }
    if (aiProperties.getQwen().getTemperature() != null) {
      builder.temperature(aiProperties.getQwen().getTemperature());
    }
    if (apiKey != null) {
      builder.apiKey(apiKey);
    }
    return builder.build();
  }

  private String buildSystemPrompt(PetEntity pet) {
    String base = resolveSystemPromptBase();
    if (pet == null) {
      return base + " 当前未关联宠物，请先询问必要信息后再给建议。";
    }
    return base + " 当前宠物信息：名称=" + safe(pet.getName())
        + "，品种=" + safe(pet.getBreed())
        + "，性别=" + pet.getGender()
        + "，生日=" + (pet.getBirthday() == null ? "未知" : pet.getBirthday())
        + "，体重kg=" + (pet.getWeightKg() == null ? "未知" : pet.getWeightKg()) + "。";
  }

  private String resolveSystemPromptBase() {
    String filePath = trimToNull(aiProperties.getQwen().getSystemPromptFile());
    if (filePath != null) {
      try {
        String text = Files.readString(Path.of(filePath));
        if (!text.isBlank()) {
          return text;
        }
      } catch (IOException e) {
        log.warn("Failed to read AI system prompt file {}: {}", filePath, e.getMessage());
      }
    }
    String configured = trimToNull(aiProperties.getQwen().getSystemPrompt());
    return configured == null ? "你是一位宠物健康与养护助手。" : configured;
  }

  private String extractContent(GenerationResult result) {
    try {
      if (result == null || result.getOutput() == null || result.getOutput().getChoices() == null
          || result.getOutput().getChoices().isEmpty()
          || result.getOutput().getChoices().get(0) == null
          || result.getOutput().getChoices().get(0).getMessage() == null) {
        return null;
      }
      Object content = result.getOutput().getChoices().get(0).getMessage().getContent();
      return content == null ? null : String.valueOf(content);
    } catch (Exception e) {
      return null;
    }
  }

  private Integer extractTokens(GenerationResult result) {
    try {
      if (result == null || result.getUsage() == null) {
        return null;
      }
      return result.getUsage().getTotalTokens();
    } catch (Exception e) {
      return null;
    }
  }

  private String extractRequestId(GenerationResult result) {
    try {
      return result == null ? null : result.getRequestId();
    } catch (Exception e) {
      return null;
    }
  }

  private String safe(Object value) {
    return value == null ? "未知" : String.valueOf(value);
  }

  private String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String v = value.trim();
    return v.isEmpty() ? null : v;
  }
}
