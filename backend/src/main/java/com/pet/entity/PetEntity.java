package com.pet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "pets")
public class PetEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(nullable = false, length = 50)
  private String name;

  /**
   * 兼容旧页面和旧接口展示使用的“可读文本”。
   *
   * <p>真实结构化分类数据在 categoryId / categoryPath / customSpeciesNote。
   * breed 在当前版本继续保留，主要是减少现有功能的兼容成本。
   */
  @Column(length = 50)
  private String breed;

  @Column(name = "category_id")
  private Long categoryId;

  @Column(name = "category_path", length = 255)
  private String categoryPath;

  @Column(name = "custom_species_note", length = 255)
  private String customSpeciesNote;

  @Column(length = 20)
  private String gender;

  @Column(name = "birth_date")
  private LocalDate birthDate;

  private Boolean neutered;

  @Column(name = "current_weight", precision = 6, scale = 2)
  private BigDecimal currentWeight;

  @Column(name = "avatar_url", length = 512)
  private String avatarUrl;

  @Column(name = "tags_json", columnDefinition = "TEXT")
  private String tagsJson;

  @Column(name = "is_primary")
  private Boolean isPrimary;

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

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getBreed() {
    return breed;
  }

  public void setBreed(String breed) {
    this.breed = breed;
  }

  public Long getCategoryId() {
    return categoryId;
  }

  public void setCategoryId(Long categoryId) {
    this.categoryId = categoryId;
  }

  public String getCategoryPath() {
    return categoryPath;
  }

  public void setCategoryPath(String categoryPath) {
    this.categoryPath = categoryPath;
  }

  public String getCustomSpeciesNote() {
    return customSpeciesNote;
  }

  public void setCustomSpeciesNote(String customSpeciesNote) {
    this.customSpeciesNote = customSpeciesNote;
  }

  public String getGender() {
    return gender;
  }

  public void setGender(String gender) {
    this.gender = gender;
  }

  public LocalDate getBirthDate() {
    return birthDate;
  }

  public void setBirthDate(LocalDate birthDate) {
    this.birthDate = birthDate;
  }

  public Boolean getNeutered() {
    return neutered;
  }

  public void setNeutered(Boolean neutered) {
    this.neutered = neutered;
  }

  public BigDecimal getCurrentWeight() {
    return currentWeight;
  }

  public void setCurrentWeight(BigDecimal currentWeight) {
    this.currentWeight = currentWeight;
  }

  public String getAvatarUrl() {
    return avatarUrl;
  }

  public void setAvatarUrl(String avatarUrl) {
    this.avatarUrl = avatarUrl;
  }

  public String getTagsJson() {
    return tagsJson;
  }

  public void setTagsJson(String tagsJson) {
    this.tagsJson = tagsJson;
  }

  public Boolean getIsPrimary() {
    return isPrimary;
  }

  public void setIsPrimary(Boolean isPrimary) {
    this.isPrimary = isPrimary;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }
}
