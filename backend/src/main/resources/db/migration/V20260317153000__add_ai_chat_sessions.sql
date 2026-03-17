CREATE TABLE ai_chat_sessions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  pet_id BIGINT,
  title VARCHAR(100) NOT NULL,
  last_message_preview VARCHAR(255),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_ai_chat_sessions_user_updated (user_id, updated_at),
  INDEX idx_ai_chat_sessions_pet_updated (pet_id, updated_at)
);

ALTER TABLE ai_chat_messages
  ADD COLUMN session_id BIGINT NULL AFTER id;

ALTER TABLE ai_chat_messages
  ADD INDEX idx_ai_chat_messages_session_created (session_id, created_at);
