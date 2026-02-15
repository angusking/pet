package com.pet.repository;

import com.pet.entity.PetEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PetRepository extends JpaRepository<PetEntity, Long> {
  List<PetEntity> findByUserIdOrderByIsPrimaryDescIdAsc(Long userId);

  Optional<PetEntity> findByIdAndUserId(Long id, Long userId);

  long countByUserId(Long userId);

  @Modifying
  @Query("update PetEntity p set p.isPrimary = false where p.userId = :userId")
  void clearPrimary(@Param("userId") Long userId);
}
