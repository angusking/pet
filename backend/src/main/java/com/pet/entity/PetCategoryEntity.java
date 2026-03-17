package com.pet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 宠物分类实体。
 *
 * <p>分类树最多三层，负责表达“是什么宠物”。
 * 颜色、体型、变异、特殊体征等额外描述，不进入分类树，而是放到宠物档案的自定义说明字段里。
 */
@Entity
@Table(name = "pet_categories")
public class PetCategoryEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(nullable = false, length = 100)
  private String code;

  @Column(name = "parent_id")
  private Long parentId;

  @Column(name = "level_num", nullable = false, columnDefinition = "TINYINT")
  private Integer levelNum;

  @Column(nullable = false, length = 255)
  private String path;

  @Column(name = "sort_num")
  private Integer sortNum;

  @Column(name = "is_active")
  private Boolean isActive;

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

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public Long getParentId() {
    return parentId;
  }

  public void setParentId(Long parentId) {
    this.parentId = parentId;
  }

  public Integer getLevelNum() {
    return levelNum;
  }

  public void setLevelNum(Integer levelNum) {
    this.levelNum = levelNum;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public Integer getSortNum() {
    return sortNum;
  }

  public void setSortNum(Integer sortNum) {
    this.sortNum = sortNum;
  }

  public Boolean getIsActive() {
    return isActive;
  }

  public void setIsActive(Boolean isActive) {
    this.isActive = isActive;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }
}
