package com.pet.repository;

import com.pet.entity.PetWeightRecordEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PetWeightRecordRepository extends JpaRepository<PetWeightRecordEntity, Long> {
  List<PetWeightRecordEntity> findByPetIdAndUserIdOrderByRecordedAtDescIdDesc(Long petId, Long userId);

  Optional<PetWeightRecordEntity> findByIdAndPetIdAndUserId(Long id, Long petId, Long userId);

  Optional<PetWeightRecordEntity> findFirstByPetIdAndUserIdOrderByRecordedAtDescIdDesc(Long petId, Long userId);

  Optional<PetWeightRecordEntity> findFirstByPetIdAndUserIdAndRecordedAtBeforeOrderByRecordedAtDescIdDesc(
      Long petId,
      Long userId,
      LocalDateTime recordedAt);

  @Modifying
  @Query("delete from PetWeightRecordEntity r where r.petId = :petId and r.userId = :userId")
  void deleteByPetIdAndUserId(@Param("petId") Long petId, @Param("userId") Long userId);
}
