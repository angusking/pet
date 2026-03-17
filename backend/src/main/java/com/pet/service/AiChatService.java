package com.pet.service;

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

  public AiChatService(
      PetRepository petRepository,
      AiChatSessionRepository aiChatSessionRepository,
      AiChatMessageRepository aiChatMessageRepository,
      AiProvider aiProvider,
      AiProperties aiProperties) {
    this.petRepository = petRepository;
    this.aiChatSessionRepository = aiChatSessionRepository;
    this.aiChatMessageRepository = aiChatMessageRepository;
    this.aiProvider = aiProvider;
    this.aiProperties = aiProperties;
  }

  /**
   * 返回当前用户的 AI 会话列表。
   *
   * <p>这里直接查数据库，而不是读内存 Map。
   * 这样服务重启后历史对话依然存在，前端也能真正显示“历史会话”。
   */
  @Transactional(readOnly = true)
  public List<AiChatSessionResponse> listSessions(Long userId) {
    return aiChatSessionRepository.findByUserIdOrderByUpdatedAtDescIdDesc(userId).stream()
        .map(this::toSessionResponse)
        .toList();
  }

  /**
   * 新建会话时只创建会话主记录，不会提前创建消息。
   */
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

  /**
   * 消息列表按时间正序返回，前端直接从上到下渲染即可。
   */
  @Transactional(readOnly = true)
  public List<AiChatMessageResponse> listMessages(Long userId, Long sessionId) {
    requireSession(userId, sessionId);
    return aiChatMessageRepository.findBySessionIdOrderByCreatedAtAscIdAsc(sessionId).stream()
        .map(this::toMessageResponse)
        .toList();
  }

  /**
   * 提供给 AIService 的内部回源接口。
   *
   * <p>这里只返回最近若干条轻量消息，用于在 Redis miss 时恢复短期上下文。
   * 依然保留用户和 session 的归属校验，避免内部接口绕过会话所有权判断。
   */
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
          message.getContent(),
          message.getCreatedAt()));
    }
    return result;
  }

  /**
   * 会话关联的宠物放在 session 表上，切换宠物时只更新这一条记录。
   */
  @Transactional
  public AiChatSessionResponse updateSessionPet(Long userId, Long sessionId, AiChatUpdatePetRequest request) {
    AiChatSessionEntity session = requireSession(userId, sessionId);
    PetEntity pet = resolvePet(userId, request == null ? null : request.petId());
    session.setPetId(pet == null ? null : pet.getId());
    session.setUpdatedAt(LocalDateTime.now());
    return toSessionResponse(aiChatSessionRepository.save(session));
  }

  /**
   * 保存一轮对话，并调用 AIService 生成回复。
   *
   * <p>当前流程是：
   * 1. 先保存 user 消息
   * 2. 再取最近消息 recentMessages 传给 AIService
   * 3. AI 返回后保存 assistant 消息
   * 4. 最后更新 session 的摘要和更新时间
   *
   * <p>这个方法放在事务里。
   * 如果 AI 调用失败或后续保存失败，这一轮 user 消息也会一起回滚，
   * 避免数据库里留下不完整的半轮对话。
   */
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
    assistantMessage.setContent(reply.content());
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

  /**
   * 每个会话允许的 user 消息条数仍然沿用现有配置。
   *
   * <p>区别只是以前从内存里数，现在从数据库里数。
   */
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

  /**
   * session 表只存 petId，所以返回给前端前需要补一份简化版宠物快照。
   */
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
    return new AiChatMessageResponse(
        state.getId(),
        state.getSessionId(),
        state.getRole(),
        state.getContent(),
        state.getModel(),
        state.getTokens(),
        state.getCreatedAt());
  }

  private AiPetContextResponse petSnapshot(PetEntity pet) {
    return new AiPetContextResponse(pet.getId(), pet.getName(), pet.getBreed(), pet.getAvatarUrl());
  }

  /**
   * 只带最近 8 条消息去调用 AIService，避免上下文无限膨胀。
   *
   * <p>数据库查询先按倒序拿最近 8 条，再在内存里翻回正序，
   * 这样最终给模型的消息顺序仍然是“旧的在前，新的在后”。
   */
  private List<AiProvider.ChatMessage> buildRecentMessages(Long sessionId) {
    List<AiChatMessageEntity> latestMessages = loadRecentMessageEntities(sessionId, RECENT_MESSAGE_LIMIT);
    if (latestMessages.isEmpty()) {
      return List.of();
    }

    List<AiProvider.ChatMessage> recentMessages = new ArrayList<>();
    for (int i = latestMessages.size() - 1; i >= 0; i--) {
      AiChatMessageEntity message = latestMessages.get(i);
      recentMessages.add(new AiProvider.ChatMessage(message.getRole(), message.getContent()));
    }
    return recentMessages;
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

  /**
   * 没有用户自定义标题时，先放一个自动标题。
   * 后续当第一轮 user 消息真正发送后，再用用户问题更新成更自然的标题。
   */
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
}
