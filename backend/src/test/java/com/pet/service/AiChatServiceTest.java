package com.pet.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pet.api.ai.dto.AiChatCreateRequest;
import com.pet.api.ai.dto.AiChatSendMessageRequest;
import com.pet.api.ai.dto.AiChatSendMessageResponse;
import com.pet.api.ai.dto.AiChatSessionResponse;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class AiChatServiceTest {
  @Mock
  private PetRepository petRepository;

  @Mock
  private AiChatSessionRepository aiChatSessionRepository;

  @Mock
  private AiChatMessageRepository aiChatMessageRepository;

  @Mock
  private AiProvider aiProvider;

  @Mock
  private AiProperties aiProperties;

  @InjectMocks
  private AiChatService aiChatService;

  @Test
  void createSessionShouldPersistSessionWithPetContext() {
    PetEntity pet = pet(8L, 1L, "Milo");
    when(petRepository.findByIdAndUserId(8L, 1L)).thenReturn(Optional.of(pet));
    when(aiChatSessionRepository.save(any(AiChatSessionEntity.class))).thenAnswer(invocation -> {
      AiChatSessionEntity entity = invocation.getArgument(0);
      entity.setId(101L);
      return entity;
    });

    AiChatSessionResponse response = aiChatService.createSession(1L, new AiChatCreateRequest(8L, null));

    assertEquals(101L, response.id());
    assertEquals(8L, response.petId());
    assertNotNull(response.pet());
    assertEquals("Milo", response.pet().name());

    ArgumentCaptor<AiChatSessionEntity> captor = ArgumentCaptor.forClass(AiChatSessionEntity.class);
    verify(aiChatSessionRepository).save(captor.capture());
    assertEquals(1L, captor.getValue().getUserId());
    assertEquals(8L, captor.getValue().getPetId());
  }

  @Test
  void sendMessageShouldPersistBothMessagesAndUpdateSession() {
    PetEntity pet = pet(8L, 1L, "Milo");
    AiChatSessionEntity session = session(33L, 1L, 8L, "新对话");
    AiChatMessageEntity previousAssistant = message(7L, 33L, 1L, 8L, "assistant", "之前的回答", LocalDateTime.now().minusMinutes(2));

    when(aiProperties.getSessionMaxUserMessages()).thenReturn(5);
    when(aiChatSessionRepository.findByIdAndUserId(33L, 1L)).thenReturn(Optional.of(session));
    when(petRepository.findByIdAndUserId(8L, 1L)).thenReturn(Optional.of(pet));
    when(aiChatMessageRepository.countBySessionIdAndRole(33L, "user")).thenReturn(1L);
    when(aiChatMessageRepository.findBySessionIdOrderByCreatedAtDescIdDesc(eq(33L), any(Pageable.class)))
        .thenReturn(List.of(message(11L, 33L, 1L, 8L, "user", "我家狗狗拉稀", LocalDateTime.now()), previousAssistant));
    when(aiProvider.generateReply(any(AiProvider.AiRequest.class)))
        .thenReturn(new AiProvider.AiReply("{\"summary\":\"ok\"}", "aiservice", 123, "trace-1"));
    when(aiChatMessageRepository.save(any(AiChatMessageEntity.class)))
        .thenAnswer(invocation -> {
          AiChatMessageEntity entity = invocation.getArgument(0);
          if (entity.getId() == null) {
            entity.setId("user".equals(entity.getRole()) ? 201L : 202L);
          }
          return entity;
        });
    when(aiChatSessionRepository.save(any(AiChatSessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

    AiChatSendMessageResponse response = aiChatService.sendMessage(1L, 33L, new AiChatSendMessageRequest("我家狗狗拉稀"));

    assertEquals(201L, response.userMessage().id());
    assertEquals(202L, response.assistantMessage().id());
    assertEquals("{\"summary\":\"ok\"}", response.assistantMessage().content());
    assertEquals("我家狗狗拉稀", response.session().title());

    ArgumentCaptor<AiProvider.AiRequest> requestCaptor = ArgumentCaptor.forClass(AiProvider.AiRequest.class);
    verify(aiProvider).generateReply(requestCaptor.capture());
    List<AiProvider.ChatMessage> recentMessages = requestCaptor.getValue().recentMessages();
    assertEquals(2, recentMessages.size());
    assertEquals("assistant", recentMessages.get(0).role());
    assertEquals("user", recentMessages.get(1).role());
  }

  @Test
  void sendMessageShouldRejectWhenUserMessageLimitReached() {
    AiChatSessionEntity session = session(33L, 1L, null, "新对话");
    when(aiProperties.getSessionMaxUserMessages()).thenReturn(2);
    when(aiChatSessionRepository.findByIdAndUserId(33L, 1L)).thenReturn(Optional.of(session));
    when(aiChatMessageRepository.countBySessionIdAndRole(33L, "user")).thenReturn(2L);

    BusinessException ex = assertThrows(BusinessException.class,
        () -> aiChatService.sendMessage(1L, 33L, new AiChatSendMessageRequest("继续问诊")));

    assertEquals(ApiError.AI_CHAT_LIMIT_REACHED, ex.getError());
    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    verify(aiProvider, never()).generateReply(any());
  }

  @Test
  void listRecentMessagesShouldReturnMessagesInChronologicalOrder() {
    AiChatSessionEntity session = session(33L, 1L, null, "会话A");
    AiChatMessageEntity latest = message(3L, 33L, 1L, null, "assistant", "第三条", LocalDateTime.now());
    AiChatMessageEntity middle = message(2L, 33L, 1L, null, "user", "第二条", LocalDateTime.now().minusMinutes(1));
    AiChatMessageEntity oldest = message(1L, 33L, 1L, null, "assistant", "第一条", LocalDateTime.now().minusMinutes(2));

    when(aiChatSessionRepository.findByIdAndUserId(33L, 1L)).thenReturn(Optional.of(session));
    when(aiChatMessageRepository.findBySessionIdOrderByCreatedAtDescIdDesc(eq(33L), any(Pageable.class)))
        .thenReturn(List.of(latest, middle, oldest));

    List<AiRecentChatMessageResponse> response = aiChatService.listRecentMessages(1L, 33L, 20);

    assertEquals(3, response.size());
    assertEquals("第一条", response.get(0).content());
    assertEquals("第三条", response.get(2).content());
  }

  @Test
  void listMessagesShouldThrowWhenSessionNotOwnedByUser() {
    when(aiChatSessionRepository.findByIdAndUserId(33L, 1L)).thenReturn(Optional.empty());

    BusinessException ex = assertThrows(BusinessException.class,
        () -> aiChatService.listMessages(1L, 33L));

    assertEquals(ApiError.VALIDATION_FAILED, ex.getError());
    assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
  }

  private static PetEntity pet(Long id, Long userId, String name) {
    PetEntity pet = new PetEntity();
    pet.setId(id);
    pet.setUserId(userId);
    pet.setName(name);
    pet.setBreed("Dog");
    pet.setBirthDate(LocalDate.of(2024, 1, 1));
    return pet;
  }

  private static AiChatSessionEntity session(Long id, Long userId, Long petId, String title) {
    AiChatSessionEntity session = new AiChatSessionEntity();
    session.setId(id);
    session.setUserId(userId);
    session.setPetId(petId);
    session.setTitle(title);
    session.setCreatedAt(LocalDateTime.now().minusMinutes(5));
    session.setUpdatedAt(LocalDateTime.now().minusMinutes(1));
    return session;
  }

  private static AiChatMessageEntity message(
      Long id,
      Long sessionId,
      Long userId,
      Long petId,
      String role,
      String content,
      LocalDateTime createdAt) {
    AiChatMessageEntity message = new AiChatMessageEntity();
    message.setId(id);
    message.setSessionId(sessionId);
    message.setUserId(userId);
    message.setPetId(petId);
    message.setRole(role);
    message.setContent(content);
    message.setCreatedAt(createdAt);
    return message;
  }
}
