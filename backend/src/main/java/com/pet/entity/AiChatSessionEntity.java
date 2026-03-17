package com.pet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * AI 会话主表。
 *
 * <p>这张表只保存“会话级别”的信息，例如：
 * 谁发起了这个会话、当前关联哪只宠物、标题、最后一条消息摘要，以及列表页常用的时间字段。
 *
 * <p>真正的消息明细不放在这里，而是放在 ai_chat_messages。
 * 这样做可以让“会话列表”和“消息明细”各查各的，职责更清楚。
 */
@Entity
@Table(name = "ai_chat_sessions")
public class AiChatSessionEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "pet_id")
  private Long petId;

  @Column(nullable = false, length = 100)
  private String title;

  @Column(name = "last_message_preview", length = 255)
  private String lastMessagePreview;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
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

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getLastMessagePreview() {
    return lastMessagePreview;
  }

  public void setLastMessagePreview(String lastMessagePreview) {
    this.lastMessagePreview = lastMessagePreview;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
