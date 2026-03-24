package com.pet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * AI 消息明细表。
 *
 * <p>一条记录代表一次 user 或 assistant 发出的单条消息。
 * sessionId 用来把多条消息归属到同一个会话里。
 *
 * <p>这里继续保留 userId 和 petId，
 * 是为了兼容现有表设计，也方便后续直接按用户或宠物排查消息数据。
 */
@Entity
@Table(name = "ai_chat_messages")
public class AiChatMessageEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "session_id")
  private Long sessionId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "pet_id")
  private Long petId;

  @Column(nullable = false, length = 20)
  private String role;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  @Column(name = "structured_payload", columnDefinition = "LONGTEXT")
  private String structuredPayload;

  @Column(length = 50)
  private String model;

  private Integer tokens;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getSessionId() {
    return sessionId;
  }

  public void setSessionId(Long sessionId) {
    this.sessionId = sessionId;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public Long getPetId() {
    return petId;
  }

  public void setPetId(Long petId) {
    this.petId = petId;
  }

  public String getRole() {
    return role;
  }

  public void setRole(String role) {
    this.role = role;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public String getStructuredPayload() {
    return structuredPayload;
  }

  public void setStructuredPayload(String structuredPayload) {
    this.structuredPayload = structuredPayload;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public Integer getTokens() {
    return tokens;
  }

  public void setTokens(Integer tokens) {
    this.tokens = tokens;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }
}
