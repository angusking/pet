package com.pet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pet_weight_records")
public class PetWeightRecordEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "pet_id", nullable = false)
  private Long petId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "weight_value", nullable = false, precision = 6, scale = 2)
  private BigDecimal weightValue;

  @Column(nullable = false, length = 10)
  private String unit;

  @Column(length = 20)
  private String source;

  @Column(length = 255)
  private String note;

  @Column(name = "recorded_at", nullable = false)
  private LocalDateTime recordedAt;

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

  public Long getPetId() {
    return petId;
  }

  public void setPetId(Long petId) {
    this.petId = petId;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public BigDecimal getWeightValue() {
    return weightValue;
  }

  public void setWeightValue(BigDecimal weightValue) {
    this.weightValue = weightValue;
  }

  public String getUnit() {
    return unit;
  }

  public void setUnit(String unit) {
    this.unit = unit;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public String getNote() {
    return note;
  }

  public void setNote(String note) {
    this.note = note;
  }

  public LocalDateTime getRecordedAt() {
    return recordedAt;
  }

  public void setRecordedAt(LocalDateTime recordedAt) {
    this.recordedAt = recordedAt;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }
}
