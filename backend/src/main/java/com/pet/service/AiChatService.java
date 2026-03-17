package com.pet.service;

import com.pet.api.ai.dto.AiChatCreateRequest;
import com.pet.api.ai.dto.AiChatMessageResponse;
import com.pet.api.ai.dto.AiChatSendMessageRequest;
import com.pet.api.ai.dto.AiChatSendMessageResponse;
import com.pet.api.ai.dto.AiChatSessionResponse;
import com.pet.api.ai.dto.AiChatUpdatePetRequest;
import com.pet.api.ai.dto.AiPetContextResponse;
import com.pet.api.error.ApiError;
import com.pet.api.error.BusinessException;
import com.pet.config.AiProperties;
import com.pet.entity.PetEntity;
import com.pet.repository.PetRepository;
import com.pet.service.ai.AiProvider;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AiChatService {
  private final PetRepository petRepository;
  private final AiProvider aiProvider;
  private final AiProperties aiProperties;

  private final AtomicLong sessionIdGen = new AtomicLong(1);
  private final AtomicLong messageIdGen = new AtomicLong(1);
  private final Map<Long, Map<Long, SessionState>> storeByUser = new ConcurrentHashMap<>();

  public AiChatService(PetRepository petRepository, AiProvider aiProvider, AiProperties aiProperties) {
    this.petRepository = petRepository;
    this.aiProvider = aiProvider;
    this.aiProperties = aiProperties;
  }

  public List<AiChatSessionResponse> listSessions(Long userId) {
    Map<Long, SessionState> sessions = storeByUser.getOrDefault(userId, Collections.emptyMap());
    return sessions.values().stream()
        .sorted(Comparator.comparing((SessionState s) -> s.updatedAt).reversed())
        .map(this::toSessionResponse)
        .toList();
  }

  public AiChatSessionResponse createSession(Long userId, AiChatCreateRequest request) {
    PetEntity pet = resolvePet(userId, request == null ? null : request.petId());
    Long sessionId = sessionIdGen.getAndIncrement();
    LocalDateTime now = LocalDateTime.now();
    String title = (request == null || request.title() == null || request.title().isBlank())
        ? (pet == null ? "新对话" : ("关于" + pet.getName()))
        : request.title().trim();
    SessionState state = new SessionState(sessionId, userId, pet == null ? null : pet.getId(), title, now);
    if (pet != null) {
      state.pet = petSnapshot(pet);
    }
    userSessions(userId).put(sessionId, state);
    return toSessionResponse(state);
  }

  public List<AiChatMessageResponse> listMessages(Long userId, Long sessionId) {
    SessionState session = requireSession(userId, sessionId);
    List<AiChatMessageResponse> result = new ArrayList<>();
    for (MessageState message : session.messages) {
      result.add(toMessageResponse(message));
    }
    return result;
  }

  public AiChatSessionResponse updateSessionPet(Long userId, Long sessionId, AiChatUpdatePetRequest request) {
    SessionState session = requireSession(userId, sessionId);
    PetEntity pet = resolvePet(userId, request == null ? null : request.petId());
    session.petId = pet == null ? null : pet.getId();
    session.pet = pet == null ? null : petSnapshot(pet);
    session.updatedAt = LocalDateTime.now();
    return toSessionResponse(session);
  }

  public AiChatSendMessageResponse sendMessage(Long userId, Long sessionId, AiChatSendMessageRequest request) {
    SessionState session = requireSession(userId, sessionId);
    validateSessionUserMessageLimit(session);
    String content = request.content().trim();
    LocalDateTime now = LocalDateTime.now();
    String traceId = buildTraceId();
    List<AiProvider.ChatMessage> recentMessages = buildRecentMessages(session.messages);

    MessageState userMsg = new MessageState(messageIdGen.getAndIncrement(), sessionId, "user", content, null, null, now);
    session.messages.add(userMsg);

    PetEntity pet = session.petId == null ? null : petRepository.findByIdAndUserId(session.petId, userId).orElse(null);
    AiProvider.AiReply reply = aiProvider.generateReply(new AiProvider.AiRequest(
        traceId,
        String.valueOf(sessionId),
        userId,
        pet,
        content,
        recentMessages));

    MessageState aiMsg = new MessageState(
        messageIdGen.getAndIncrement(),
        sessionId,
        "assistant",
        reply.content(),
        reply.model(),
        reply.tokens(),
        LocalDateTime.now());
    session.messages.add(aiMsg);

    session.updatedAt = aiMsg.createdAt;
    session.lastMessagePreview = aiMsg.content.length() > 40 ? aiMsg.content.substring(0, 40) : aiMsg.content;
    if ("新对话".equals(session.title) || session.title.startsWith("关于")) {
      session.title = content.length() > 12 ? content.substring(0, 12) + "..." : content;
    }

    return new AiChatSendMessageResponse(toSessionResponse(session), toMessageResponse(userMsg), toMessageResponse(aiMsg));
  }

  private void validateSessionUserMessageLimit(SessionState session) {
    Integer max = aiProperties.getSessionMaxUserMessages();
    if (max == null || max <= 0) {
      return;
    }
    long userMsgCount = session.messages.stream()
        .filter(message -> "user".equals(message.role()))
        .count();
    if (userMsgCount >= max) {
      throw new BusinessException(ApiError.AI_CHAT_LIMIT_REACHED, HttpStatus.BAD_REQUEST);
    }
  }

  private Map<Long, SessionState> userSessions(Long userId) {
    return storeByUser.computeIfAbsent(userId, ignored -> new LinkedHashMap<>());
  }

  private SessionState requireSession(Long userId, Long sessionId) {
    SessionState session = userSessions(userId).get(sessionId);
    if (session == null) {
      throw new BusinessException(ApiError.VALIDATION_FAILED, HttpStatus.NOT_FOUND);
    }
    return session;
  }

  private PetEntity resolvePet(Long userId, Long petId) {
    if (petId == null) {
      return null;
    }
    return petRepository.findByIdAndUserId(petId, userId)
        .orElseThrow(() -> new BusinessException(ApiError.PET_NOT_FOUND, HttpStatus.NOT_FOUND));
  }

  private AiChatSessionResponse toSessionResponse(SessionState state) {
    return new AiChatSessionResponse(
        state.id,
        state.title,
        state.petId,
        state.pet,
        state.lastMessagePreview,
        state.updatedAt,
        state.createdAt);
  }

  private AiChatMessageResponse toMessageResponse(MessageState state) {
    return new AiChatMessageResponse(state.id, state.sessionId, state.role, state.content, state.model, state.tokens, state.createdAt);
  }

  private AiPetContextResponse petSnapshot(PetEntity pet) {
    return new AiPetContextResponse(pet.getId(), pet.getName(), pet.getBreed(), pet.getAvatarUrl());
  }

  private List<AiProvider.ChatMessage> buildRecentMessages(List<MessageState> messages) {
    if (messages == null || messages.isEmpty()) {
      return List.of();
    }
    int fromIndex = Math.max(0, messages.size() - 8);
    List<AiProvider.ChatMessage> recentMessages = new ArrayList<>();
    for (int i = fromIndex; i < messages.size(); i++) {
      MessageState message = messages.get(i);
      recentMessages.add(new AiProvider.ChatMessage(message.role(), message.content()));
    }
    return recentMessages;
  }

  private String buildTraceId() {
    String timeHex = Long.toHexString(System.currentTimeMillis());
    if (timeHex.length() > 6) {
      timeHex = timeHex.substring(timeHex.length() - 6);
    }
    String rnd = Integer.toHexString(ThreadLocalRandom.current().nextInt(0x10000));
    return "ai-" + timeHex + "-" + rnd;
  }

  private static final class SessionState {
    private final Long id;
    private final Long userId;
    private Long petId;
    private String title;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String lastMessagePreview;
    private AiPetContextResponse pet;
    private final List<MessageState> messages = new ArrayList<>();

    private SessionState(Long id, Long userId, Long petId, String title, LocalDateTime now) {
      this.id = id;
      this.userId = userId;
      this.petId = petId;
      this.title = title;
      this.createdAt = now;
      this.updatedAt = now;
    }
  }

  private record MessageState(
      Long id,
      Long sessionId,
      String role,
      String content,
      String model,
      Integer tokens,
      LocalDateTime createdAt
  ) {}
}
