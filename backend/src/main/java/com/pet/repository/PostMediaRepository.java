package com.pet.repository;

import com.pet.entity.PostMediaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostMediaRepository extends JpaRepository<PostMediaEntity, Long> {
  List<PostMediaEntity> findByPostIdInOrderByPostIdAscSortOrderAsc(List<Long> postIds);
  void deleteByPostId(Long postId);
}
