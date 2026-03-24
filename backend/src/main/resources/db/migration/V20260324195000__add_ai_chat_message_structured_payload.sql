ALTER TABLE ai_chat_messages
  ADD COLUMN structured_payload LONGTEXT NULL AFTER content;
