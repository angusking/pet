package com.pet.repository;

import com.pet.entity.PostCommentEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostCommentRepository extends JpaRepository<PostCommentEntity, Long> {
  List<PostCommentEntity> findByPostIdOrderByCreatedAtAsc(Long postId);
  long countByPostId(Long postId);
  void deleteByPostId(Long postId);
  Optional<PostCommentEntity> findByIdAndPostId(Long id, Long postId);
}
