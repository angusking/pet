CREATE TABLE post_likes (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  post_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_post_likes_post_user (post_id, user_id),
  INDEX idx_post_likes_post (post_id),
  INDEX idx_post_likes_user (user_id)
);

CREATE TABLE post_comments (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  post_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  content VARCHAR(500) NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_post_comments_post_created (post_id, created_at),
  INDEX idx_post_comments_user_created (user_id, created_at)
);
