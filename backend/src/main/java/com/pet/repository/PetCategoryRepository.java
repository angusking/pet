package com.pet.repository;

import com.pet.entity.PetCategoryEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PetCategoryRepository extends JpaRepository<PetCategoryEntity, Long> {
  List<PetCategoryEntity> findByIsActiveTrueOrderByLevelNumAscSortNumAscIdAsc();

  Optional<PetCategoryEntity> findByIdAndIsActiveTrue(Long id);
}
