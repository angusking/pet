package com.pet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pet.api.ai.dto.AiChatCreateRequest;
import com.pet.api.ai.dto.AiChatMessageResponse;
import com.pet.api.ai.dto.AiChatSendMessageRequest;
import com.pet.api.ai.dto.AiChatSendMessageResponse;
import com.pet.api.ai.dto.AiChatSessionResponse;
import com.pet.api.ai.dto.AiChatUpdatePetRequest;
import com.pet.api.ai.dto.AiPetContextResponse;
import com.pet.api.ai.dto.AiRecentChatMessageResponse;
import com.pet.api.error.ApiError;
import com.pet.api.error.BusinessException;
import com.pet.config.AiProperties;
import com.pet.entity.AiChatMessageEntity;
import com.pet.entity.AiChatSessionEntity;
import com.pet.entity.PetEntity;
import com.pet.repository.AiChatMessageRepository;
import com.pet.repository.AiChatSessionRepository;
import com.pet.repository.PetRepository;
import com.pet.service.ai.AiProvider;
import com.pet.service.ai.AiServiceChatResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiChatService {
  private static final int RECENT_MESSAGE_LIMIT = 8;

  private final PetRepository petRepository;
  private final AiChatSessionRepository aiChatSessionRepository;
  private final AiChatMessageRepository aiChatMessageRepository;
  private final AiProvider aiProvider;
  private final AiProperties aiProperties;
  private final ObjectMapper objectMapper;

  public AiChatService(
      PetRepository petRepository,
      AiChatSessionRepository aiChatSessionRepository,
      AiChatMessageRepository aiChatMessageRepository,
      AiProvider aiProvider,
      AiProperties aiProperties,
      ObjectMapper objectMapper) {
    this.petRepository = petRepository;
    this.aiChatSessionRepository = aiChatSessionRepository;
    this.aiChatMessageRepository = aiChatMessageRepository;
    this.aiProvider = aiProvider;
    this.aiProperties = aiProperties;
    this.objectMapper = objectMapper;
  }

  @Transactional(readOnly = true)
  public List<AiChatSessionResponse> listSessions(Long userId) {
    return aiChatSessionRepository.findByUserIdOrderByUpdatedAtDescIdDesc(userId).stream()
        .map(this::toSessionResponse)
        .toList();
  }

  @Transactional
  public AiChatSessionResponse createSession(Long userId, AiChatCreateRequest request) {
    PetEntity pet = resolvePet(userId, request == null ? null : request.petId());
    LocalDateTime now = LocalDateTime.now();

    AiChatSessionEntity session = new AiChatSessionEntity();
    session.setUserId(userId);
    session.setPetId(pet == null ? null : pet.getId());
    session.setTitle(defaultSessionTitle(pet, request == null ? null : request.title()));
    session.setCreatedAt(now);
    session.setUpdatedAt(now);
    session.setLastMessagePreview(null);

    return toSessionResponse(aiChatSessionRepository.save(session));
  }

  @Transactional(readOnly = true)
  public List<AiChatMessageResponse> listMessages(Long userId, Long sessionId) {
    requireSession(userId, sessionId);
    return aiChatMessageRepository.findBySessionIdOrderByCreatedAtAscIdAsc(sessionId).stream()
        .map(this::toMessageResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<AiRecentChatMessageResponse> listRecentMessages(Long userId, Long sessionId, Integer limit) {
    requireSession(userId, sessionId);
    int resolvedLimit = normalizeRecentMessageLimit(limit);
    List<AiChatMessageEntity> latestMessages = aiChatMessageRepository.findBySessionIdOrderByCreatedAtDescIdDesc(
        sessionId,
        PageRequest.of(
            0,
            resolvedLimit,
            Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))));

    List<AiRecentChatMessageResponse> result = new ArrayList<>();
    for (int i = latestMessages.size() - 1; i >= 0; i--) {
      AiChatMessageEntity message = latestMessages.get(i);
      result.add(new AiRecentChatMessageResponse(
          message.getRole(),
          normalizeAssistantContentIfNeeded(
              message.getRole(),
              message.getContent(),
              message.getStructuredPayload()),
          message.getCreatedAt()));
    }
    return result;
  }

  @Transactional
  public AiChatSessionResponse updateSessionPet(Long userId, Long sessionId, AiChatUpdatePetRequest request) {
    AiChatSessionEntity session = requireSession(userId, sessionId);
    PetEntity pet = resolvePet(userId, request == null ? null : request.petId());
    session.setPetId(pet == null ? null : pet.getId());
    session.setUpdatedAt(LocalDateTime.now());
    return toSessionResponse(aiChatSessionRepository.save(session));
  }

  @Transactional
  public AiChatSendMessageResponse sendMessage(Long userId, Long sessionId, AiChatSendMessageRequest request) {
    AiChatSessionEntity session = requireSession(userId, sessionId);
    validateSessionUserMessageLimit(session);

    String content = request.content().trim();
    LocalDateTime now = LocalDateTime.now();
    PetEntity pet = resolvePet(userId, session.getPetId());

    AiChatMessageEntity userMessage = new AiChatMessageEntity();
    userMessage.setSessionId(sessionId);
    userMessage.setUserId(userId);
    userMessage.setPetId(session.getPetId());
    userMessage.setRole("user");
    userMessage.setContent(content);
    userMessage.setCreatedAt(now);
    AiChatMessageEntity savedUserMessage = aiChatMessageRepository.save(userMessage);

    List<AiProvider.ChatMessage> recentMessages = buildRecentMessages(sessionId);
    AiProvider.AiReply reply = aiProvider.generateReply(new AiProvider.AiRequest(
        buildTraceId(),
        String.valueOf(sessionId),
        userId,
        pet,
        content,
        recentMessages));

    AiChatMessageEntity assistantMessage = new AiChatMessageEntity();
    assistantMessage.setSessionId(sessionId);
    assistantMessage.setUserId(userId);
    assistantMessage.setPetId(session.getPetId());
    assistantMessage.setRole("assistant");
    // assistant.content 只保存用户可直接看到的正文；
    // 完整结构化数据存进 structuredPayload，供前端渲染标签和卡片。
    assistantMessage.setContent(normalizeAssistantContent(reply.content(), reply.structuredPayload()));
    assistantMessage.setStructuredPayload(normalizeStructuredPayload(reply.structuredPayload(), reply.content()));
    assistantMessage.setModel(reply.model());
    assistantMessage.setTokens(reply.tokens());
    assistantMessage.setCreatedAt(LocalDateTime.now());
    AiChatMessageEntity savedAssistantMessage = aiChatMessageRepository.save(assistantMessage);

    session.setUpdatedAt(savedAssistantMessage.getCreatedAt());
    session.setLastMessagePreview(previewOf(savedAssistantMessage.getContent(), 255));
    if (isAutoGeneratedTitle(session.getTitle(), pet)) {
      session.setTitle(titleFromFirstUserMessage(content));
    }
    AiChatSessionEntity savedSession = aiChatSessionRepository.save(session);

    return new AiChatSendMessageResponse(
        toSessionResponse(savedSession),
        toMessageResponse(savedUserMessage),
        toMessageResponse(savedAssistantMessage));
  }

  private void validateSessionUserMessageLimit(AiChatSessionEntity session) {
    Integer max = aiProperties.getSessionMaxUserMessages();
    if (max == null || max <= 0) {
      return;
    }
    long userMsgCount = aiChatMessageRepository.countBySessionIdAndRole(session.getId(), "user");
    if (userMsgCount >= max) {
      throw new BusinessException(ApiError.AI_CHAT_LIMIT_REACHED, HttpStatus.BAD_REQUEST);
    }
  }

  private AiChatSessionEntity requireSession(Long userId, Long sessionId) {
    return aiChatSessionRepository.findByIdAndUserId(sessionId, userId)
        .orElseThrow(() -> new BusinessException(ApiError.VALIDATION_FAILED, HttpStatus.NOT_FOUND));
  }

  private PetEntity resolvePet(Long userId, Long petId) {
    if (petId == null) {
      return null;
    }
    return petRepository.findByIdAndUserId(petId, userId)
        .orElseThrow(() -> new BusinessException(ApiError.PET_NOT_FOUND, HttpStatus.NOT_FOUND));
  }

  private AiChatSessionResponse toSessionResponse(AiChatSessionEntity state) {
    PetEntity pet = state.getPetId() == null
        ? null
        : petRepository.findByIdAndUserId(state.getPetId(), state.getUserId()).orElse(null);
    return new AiChatSessionResponse(
        state.getId(),
        state.getTitle(),
        state.getPetId(),
        pet == null ? null : petSnapshot(pet),
        state.getLastMessagePreview(),
        state.getUpdatedAt(),
        state.getCreatedAt());
  }

  private AiChatMessageResponse toMessageResponse(AiChatMessageEntity state) {
    StructuredAssistantPayload payload = parseStructuredPayload(
        state.getRole(),
        state.getStructuredPayload(),
        state.getContent());
    return new AiChatMessageResponse(
        state.getId(),
        state.getSessionId(),
        state.getRole(),
        normalizeAssistantContentIfNeeded(state.getRole(), state.getContent(), state.getStructuredPayload()),
        state.getModel(),
        state.getTokens(),
        state.getCreatedAt(),
        payload.intent(),
        payload.riskLevel(),
        payload.checklist(),
        payload.services(),
        payload.followUps(),
        payload.followUpQuestions(),
        payload.actionCards(),
        payload.disclaimer());
  }

  private AiPetContextResponse petSnapshot(PetEntity pet) {
    return new AiPetContextResponse(pet.getId(), pet.getName(), pet.getBreed(), pet.getAvatarUrl());
  }

  private List<AiProvider.ChatMessage> buildRecentMessages(Long sessionId) {
    List<AiChatMessageEntity> latestMessages = loadRecentMessageEntities(sessionId, RECENT_MESSAGE_LIMIT);
    if (latestMessages.isEmpty()) {
      return List.of();
    }

    List<AiProvider.ChatMessage> recentMessages = new ArrayList<>();
    for (int i = latestMessages.size() - 1; i >= 0; i--) {
      AiChatMessageEntity message = latestMessages.get(i);
      recentMessages.add(new AiProvider.ChatMessage(
          message.getRole(),
          normalizeAssistantContentIfNeeded(
              message.getRole(),
              message.getContent(),
              message.getStructuredPayload())));
    }
    return recentMessages;
  }

  private String normalizeAssistantContentIfNeeded(String role, String content, String structuredPayload) {
    if (!"assistant".equals(role)) {
      return content;
    }
    return normalizeAssistantContent(content, structuredPayload);
  }

  /**
   * assistant 正文优先用结构化 payload 里的 answer。
   * 这样即使历史 content 曾经误存为整段 JSON，也能稳定抽取出正文。
   */
  private String normalizeAssistantContent(String content, String structuredPayload) {
    StructuredAssistantPayload payload = parseStructuredPayload("assistant", structuredPayload, content);
    if (payload.answer() != null && !payload.answer().isBlank()) {
      return payload.answer();
    }
    return content;
  }

  private String normalizeStructuredPayload(String structuredPayload, String content) {
    if (structuredPayload != null && !structuredPayload.isBlank()) {
      return structuredPayload;
    }
    if (content != null && content.trim().startsWith("{")) {
      return content;
    }
    return null;
  }

  private StructuredAssistantPayload parseStructuredPayload(String role, String structuredPayload, String content) {
    if (!"assistant".equals(role)) {
      return StructuredAssistantPayload.empty();
    }
    String payloadText = structuredPayload;
    if ((payloadText == null || payloadText.isBlank()) && content != null && content.trim().startsWith("{")) {
      payloadText = content;
    }
    if (payloadText == null || payloadText.isBlank()) {
      return StructuredAssistantPayload.empty();
    }

    try {
      AiServiceChatResponse payload = objectMapper.readValue(payloadText, AiServiceChatResponse.class);
      List<String> followUps = payload.followUps() == null ? List.of() : payload.followUps();
      List<String> followUpQuestions = payload.followUpQuestions() == null || payload.followUpQuestions().isEmpty()
          ? followUps
          : payload.followUpQuestions();
      List<AiChatMessageResponse.ServiceItem> services = payload.services() == null
          ? List.of()
          : payload.services().stream()
              .map(item -> new AiChatMessageResponse.ServiceItem(item.name(), item.description(), item.url()))
              .toList();
      List<AiChatMessageResponse.ActionCard> actionCards = payload.actionCards() == null
          ? List.of()
          : payload.actionCards().stream()
              .map(card -> new AiChatMessageResponse.ActionCard(
                  card.title(),
                  card.items() == null ? List.of() : card.items()))
              .toList();
      return new StructuredAssistantPayload(
          payload.intent() == null ? "UNKNOWN" : payload.intent(),
          payload.answer(),
          payload.riskLevel(),
          payload.checklist() == null ? List.of() : payload.checklist(),
          services,
          followUps,
          followUpQuestions,
          actionCards,
          payload.disclaimer());
    } catch (Exception ignored) {
      return StructuredAssistantPayload.empty();
    }
  }

  private List<AiChatMessageEntity> loadRecentMessageEntities(Long sessionId, int limit) {
    return aiChatMessageRepository.findBySessionIdOrderByCreatedAtDescIdDesc(
        sessionId,
        PageRequest.of(
            0,
            limit,
            Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))));
  }

  private int normalizeRecentMessageLimit(Integer requestedLimit) {
    if (requestedLimit == null || requestedLimit <= 0) {
      return RECENT_MESSAGE_LIMIT;
    }
    return Math.min(requestedLimit, RECENT_MESSAGE_LIMIT);
  }

  private String buildTraceId() {
    String timeHex = Long.toHexString(System.currentTimeMillis());
    if (timeHex.length() > 6) {
      timeHex = timeHex.substring(timeHex.length() - 6);
    }
    String rnd = Integer.toHexString(ThreadLocalRandom.current().nextInt(0x10000));
    return "ai-" + timeHex + "-" + rnd;
  }

  private String defaultSessionTitle(PetEntity pet, String requestedTitle) {
    if (requestedTitle != null && !requestedTitle.isBlank()) {
      return previewOf(requestedTitle.trim(), 100);
    }
    return pet == null ? "新对话" : previewOf("关于" + pet.getName(), 100);
  }

  private String previewOf(String text, int maxLength) {
    if (text == null) {
      return null;
    }
    return text.length() > maxLength ? text.substring(0, maxLength) : text;
  }

  private boolean isAutoGeneratedTitle(String title, PetEntity pet) {
    if ("新对话".equals(title)) {
      return true;
    }
    return pet != null && ("关于" + pet.getName()).equals(title);
  }

  private String titleFromFirstUserMessage(String content) {
    String normalized = content == null ? "" : content.trim();
    if (normalized.isEmpty()) {
      return "新对话";
    }
    return normalized.length() > 12 ? normalized.substring(0, 12) + "..." : normalized;
  }

  /**
   * 后端内部使用的结构化消息快照。
   *
   * <p>这里不额外暴露成独立 DTO，避免把解析逻辑散落到多个地方。
   */
  private record StructuredAssistantPayload(
      String intent,
      String answer,
      String riskLevel,
      List<String> checklist,
      List<AiChatMessageResponse.ServiceItem> services,
      List<String> followUps,
      List<String> followUpQuestions,
      List<AiChatMessageResponse.ActionCard> actionCards,
      String disclaimer
  ) {
    private static StructuredAssistantPayload empty() {
      return new StructuredAssistantPayload(
          "UNKNOWN",
          null,
          null,
          List.of(),
          List.of(),
          List.of(),
          List.of(),
          List.of(),
          null);
    }
  }
}
