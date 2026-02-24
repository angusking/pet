package com.pet.repository;

import com.pet.entity.PostEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<PostEntity, Long> {
  Page<PostEntity> findByPetIdOrderByCreatedAtDesc(Long petId, Pageable pageable);
  Page<PostEntity> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
