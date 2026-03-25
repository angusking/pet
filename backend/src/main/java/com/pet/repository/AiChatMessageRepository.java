package com.pet.repository;

import com.pet.entity.AiChatMessageEntity;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiChatMessageRepository extends JpaRepository<AiChatMessageEntity, Long> {
  List<AiChatMessageEntity> findBySessionIdOrderByCreatedAtAscIdAsc(Long sessionId);

  List<AiChatMessageEntity> findBySessionIdOrderByCreatedAtDescIdDesc(Long sessionId, Pageable pageable);

  long countBySessionIdAndRole(Long sessionId, String role);

  void deleteBySessionId(Long sessionId);
}
