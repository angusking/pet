package com.pet.repository;

import com.pet.entity.PostLikeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostLikeRepository extends JpaRepository<PostLikeEntity, Long> {
  boolean existsByPostIdAndUserId(Long postId, Long userId);
  long countByPostId(Long postId);
  long deleteByPostIdAndUserId(Long postId, Long userId);
  void deleteByPostId(Long postId);
  List<PostLikeEntity> findByPostIdOrderByCreatedAtDesc(Long postId);
}
