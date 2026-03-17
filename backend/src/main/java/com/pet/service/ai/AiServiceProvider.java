package com.pet.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pet.config.AiProperties;
import com.pet.entity.PetEntity;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "aiservice")
public class AiServiceProvider implements AiProvider {
  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  public AiServiceProvider(RestClient.Builder restClientBuilder, AiProperties aiProperties, ObjectMapper objectMapper) {
    this.restClient = restClientBuilder
        .baseUrl(aiProperties.getAiService().getBaseUrl())
        .build();
    this.objectMapper = objectMapper;
  }

  @Override
  public AiReply generateReply(AiRequest request) {
    AiServiceChatRequest payload = new AiServiceChatRequest(
        request.requestId(),
        request.conversationId(),
        request.userId(),
        toPetInfo(request.pet()),
        request.userMessage(),
        toRecentMessages(request.recentMessages()),
        new AiServiceChatRequest.BizData(List.of(), List.of()));

    try {
      AiServiceChatResponse response = restClient.post()
          .uri("/api/ai/chat")
          .contentType(MediaType.APPLICATION_JSON)
          .accept(MediaType.APPLICATION_JSON)
          .body(payload)
          .retrieve()
          .body(AiServiceChatResponse.class);

      if (response == null) {
        throw new IllegalStateException("AIService returned empty response");
      }

      return new AiReply(
          objectMapper.writeValueAsString(response),
          "aiservice",
          null,
          response.requestId());
    } catch (RestClientException e) {
      throw new IllegalStateException("Failed to call AIService: " + e.getMessage(), e);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize AIService response", e);
    }
  }

  private List<AiServiceChatRequest.RecentMessage> toRecentMessages(List<ChatMessage> recentMessages) {
    if (recentMessages == null || recentMessages.isEmpty()) {
      return List.of();
    }
    return recentMessages.stream()
        .filter(message -> message != null && message.role() != null && message.content() != null)
        .map(message -> new AiServiceChatRequest.RecentMessage(message.role(), message.content()))
        .toList();
  }

  private AiServiceChatRequest.PetInfo toPetInfo(PetEntity pet) {
    if (pet == null || pet.getId() == null) {
      return null;
    }
    return new AiServiceChatRequest.PetInfo(
        pet.getId(),
        pet.getName(),
        inferPetType(pet.getBreed()),
        calculateAge(pet.getBirthday()),
        pet.getWeightKg() == null ? null : pet.getWeightKg().setScale(1, RoundingMode.HALF_UP).doubleValue());
  }

  private String inferPetType(String breed) {
    if (breed == null || breed.isBlank()) {
      return "";
    }
    String normalized = breed.trim().toLowerCase();
    if (normalized.contains("猫") || normalized.contains("cat")) {
      return "cat";
    }
    if (normalized.contains("狗") || normalized.contains("犬") || normalized.contains("dog")) {
      return "dog";
    }
    return "";
  }

  private Double calculateAge(LocalDate birthday) {
    if (birthday == null) {
      return null;
    }
    Period period = Period.between(birthday, LocalDate.now());
    double age = period.getYears() + (period.getMonths() / 12.0);
    return Math.round(age * 10.0) / 10.0;
  }
}
