package com.pet.service;

import com.pet.api.pet.dto.PetCategoryTreeResponse;
import com.pet.entity.PetCategoryEntity;
import com.pet.repository.PetCategoryRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PetCategoryService {
  private final PetCategoryRepository petCategoryRepository;

  public PetCategoryService(PetCategoryRepository petCategoryRepository) {
    this.petCategoryRepository = petCategoryRepository;
  }

  /**
   * 返回启用状态的完整宠物分类树。
   */
  @Transactional(readOnly = true)
  public List<PetCategoryTreeResponse> listCategoryTree() {
    List<PetCategoryEntity> categories = petCategoryRepository.findByIsActiveTrueOrderByLevelNumAscSortNumAscIdAsc();
    Map<Long, PetCategoryTreeResponse> nodeById = new LinkedHashMap<>();
    List<PetCategoryTreeResponse> roots = new ArrayList<>();
    for (PetCategoryEntity category : categories) {
      PetCategoryTreeResponse node = new PetCategoryTreeResponse(
          category.getId(),
          category.getName(),
          category.getCode(),
          category.getPath());
      nodeById.put(category.getId(), node);
      if (category.getParentId() == null) {
        roots.add(node);
        continue;
      }
      PetCategoryTreeResponse parent = nodeById.get(category.getParentId());
      if (parent != null) {
        parent.getChildren().add(node);
      }
    }
    return roots;
  }
}
