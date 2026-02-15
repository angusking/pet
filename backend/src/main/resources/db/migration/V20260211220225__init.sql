CREATE TABLE users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  nickname VARCHAR(50),
  avatar_url VARCHAR(512),
  role VARCHAR(20) DEFAULT 'USER',
  status VARCHAR(20) DEFAULT 'ACTIVE',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE pets (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  name VARCHAR(50) NOT NULL,
  breed VARCHAR(50),
  gender TINYINT DEFAULT 0,
  birthday DATE,
  weight_kg DECIMAL(5,2),
  avatar_url VARCHAR(512),
  tags_json TEXT,
  is_primary TINYINT(1) DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_pets_user (user_id),
  INDEX idx_pets_primary (user_id, is_primary)
);

CREATE TABLE posts (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  pet_id BIGINT,
  content TEXT NOT NULL,
  city VARCHAR(50),
  status VARCHAR(20) DEFAULT 'PUBLISHED',
  like_count INT DEFAULT 0,
  comment_count INT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_posts_created (created_at),
  INDEX idx_posts_user_created (user_id, created_at),
  INDEX idx_posts_pet_created (pet_id, created_at)
);

CREATE TABLE post_media (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  post_id BIGINT NOT NULL,
  media_type VARCHAR(20) DEFAULT 'IMAGE',
  url VARCHAR(1024) NOT NULL,
  sort_order INT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_media_post_sort (post_id, sort_order)
);

CREATE TABLE ads (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  ad_type VARCHAR(20) NOT NULL,
  title VARCHAR(100) NOT NULL,
  cover_url VARCHAR(1024),
  description VARCHAR(255),
  cta_text VARCHAR(20),
  landing_url VARCHAR(1024),
  weight INT DEFAULT 100,
  enabled TINYINT(1) DEFAULT 1,
  start_at DATETIME,
  end_at DATETIME,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_ads_enabled_weight (enabled, weight),
  INDEX idx_ads_type_enabled (ad_type, enabled)
);

CREATE TABLE ai_chat_messages (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  pet_id BIGINT,
  role VARCHAR(20) NOT NULL,
  content TEXT NOT NULL,
  model VARCHAR(50),
  tokens INT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_ai_user_created (user_id, created_at),
  INDEX idx_ai_pet_created (pet_id, created_at)
);
