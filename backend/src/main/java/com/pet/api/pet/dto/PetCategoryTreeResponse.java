package com.pet.api.pet.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 宠物分类树节点。
 *
 * <p>children 保持可变列表，便于 service 按 parentId 逐层拼装分类树。
 */
public class PetCategoryTreeResponse {
  private Long id;
  private String name;
  private String code;
  private String path;
  private List<PetCategoryTreeResponse> children = new ArrayList<>();

  public PetCategoryTreeResponse() {}

  public PetCategoryTreeResponse(Long id, String name, String code, String path) {
    this.id = id;
    this.name = name;
    this.code = code;
    this.path = path;
  }

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

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public List<PetCategoryTreeResponse> getChildren() {
    return children;
  }

  public void setChildren(List<PetCategoryTreeResponse> children) {
    this.children = children;
  }
}
