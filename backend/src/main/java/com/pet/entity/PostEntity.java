package com.pet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "posts")
public class PostEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "pet_id")
  private Long petId;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  @Column(length = 50)
  private String city;

  @Column(name = "tags_json", columnDefinition = "TEXT")
  private String tagsJson;

  @Column(length = 20)
  private String status;

  @Column(name = "like_count")
  private Integer likeCount;

  @Column(name = "comment_count")
  private Integer commentCount;

  @Column(name = "created_at", insertable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", insertable = false, updatable = false)
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

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public String getCity() {
    return city;
  }

  public void setCity(String city) {
    this.city = city;
  }

  public String getTagsJson() {
    return tagsJson;
  }

  public void setTagsJson(String tagsJson) {
    this.tagsJson = tagsJson;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Integer getLikeCount() {
    return likeCount;
  }

  public void setLikeCount(Integer likeCount) {
    this.likeCount = likeCount;
  }

  public Integer getCommentCount() {
    return commentCount;
  }

  public void setCommentCount(Integer commentCount) {
    this.commentCount = commentCount;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }
}
