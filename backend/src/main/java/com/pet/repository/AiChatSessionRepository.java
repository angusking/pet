package com.pet.repository;

import com.pet.entity.AiChatSessionEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiChatSessionRepository extends JpaRepository<AiChatSessionEntity, Long> {
  List<AiChatSessionEntity> findByUserIdOrderByUpdatedAtDescIdDesc(Long userId);

  Optional<AiChatSessionEntity> findByIdAndUserId(Long id, Long userId);
}
