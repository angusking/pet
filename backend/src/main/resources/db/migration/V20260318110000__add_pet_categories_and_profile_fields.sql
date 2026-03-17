CREATE TABLE pet_categories (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL COMMENT '分类名称（中文）',
  code VARCHAR(100) NOT NULL COMMENT '分类编码（英文唯一）',
  parent_id BIGINT NULL COMMENT '父级ID',
  level_num TINYINT NOT NULL COMMENT '层级：1/2/3',
  path VARCHAR(255) NOT NULL COMMENT '路径：bird/parrot/cockatiel',
  sort_num INT DEFAULT 0,
  is_active TINYINT(1) DEFAULT 1,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_pet_categories_code (code),
  KEY idx_pet_categories_parent (parent_id),
  KEY idx_pet_categories_level (level_num)
);

INSERT INTO pet_categories (id, name, code, parent_id, level_num, path, sort_num) VALUES
  (1, '猫类', 'cat', NULL, 1, 'cat', 10),
  (2, '狗类', 'dog', NULL, 1, 'dog', 20),
  (3, '鸟类', 'bird', NULL, 1, 'bird', 30),
  (4, '啮齿类', 'rodent', NULL, 1, 'rodent', 40),
  (5, '兔类', 'rabbit', NULL, 1, 'rabbit', 50),
  (6, '爬宠', 'reptile', NULL, 1, 'reptile', 60),
  (7, '水族', 'fish', NULL, 1, 'fish', 70),
  (8, '其他', 'other', NULL, 1, 'other', 80),
  (9, '短毛猫', 'cat_short', 1, 2, 'cat/cat_short', 10),
  (10, '长毛猫', 'cat_long', 1, 2, 'cat/cat_long', 20),
  (11, '小型犬', 'dog_small', 2, 2, 'dog/dog_small', 10),
  (12, '中型犬', 'dog_medium', 2, 2, 'dog/dog_medium', 20),
  (13, '大型犬', 'dog_large', 2, 2, 'dog/dog_large', 30),
  (14, '鹦鹉', 'bird_parrot', 3, 2, 'bird/parrot', 10),
  (15, '鸣禽', 'bird_song', 3, 2, 'bird/songbird', 20),
  (16, '仓鼠', 'rodent_hamster', 4, 2, 'rodent/hamster', 10),
  (17, '鼠类', 'rodent_rat', 4, 2, 'rodent/rat', 20),
  (18, '宠物兔', 'rabbit_pet', 5, 2, 'rabbit/pet', 10),
  (19, '蜥蜴', 'reptile_lizard', 6, 2, 'reptile/lizard', 10),
  (20, '蛇类', 'reptile_snake', 6, 2, 'reptile/snake', 20),
  (21, '观赏鱼', 'fish_aqua', 7, 2, 'fish/aqua', 10),
  (22, '英国短毛猫', 'british_shorthair', 9, 3, 'cat/cat_short/british_shorthair', 10),
  (23, '布偶猫', 'ragdoll', 10, 3, 'cat/cat_long/ragdoll', 10),
  (24, '金毛', 'golden_retriever', 13, 3, 'dog/dog_large/golden_retriever', 10),
  (25, '柯基', 'corgi', 11, 3, 'dog/dog_small/corgi', 10),
  (26, '玄凤鹦鹉', 'cockatiel', 14, 3, 'bird/parrot/cockatiel', 10),
  (27, '虎皮鹦鹉', 'budgerigar', 14, 3, 'bird/parrot/budgerigar', 20),
  (28, '金丝熊', 'syrian_hamster', 16, 3, 'rodent/hamster/syrian', 10),
  (29, '花枝鼠', 'fancy_rat', 17, 3, 'rodent/rat/fancy', 10),
  (30, '豹纹守宫', 'leopard_gecko', 19, 3, 'reptile/lizard/leopard_gecko', 10);

ALTER TABLE pets
  ADD COLUMN category_id BIGINT NULL COMMENT '分类ID（指向 pet_categories.id）' AFTER user_id,
  ADD COLUMN category_path VARCHAR(255) NULL COMMENT '分类路径（冗余存储）' AFTER category_id,
  ADD COLUMN custom_species_note VARCHAR(255) NULL COMMENT '用户补充说明' AFTER category_path,
  CHANGE COLUMN gender gender VARCHAR(20) NULL COMMENT 'male/female/unknown',
  CHANGE COLUMN birthday birth_date DATE NULL COMMENT '出生日期',
  ADD COLUMN neutered TINYINT(1) NULL COMMENT '是否绝育' AFTER birth_date,
  CHANGE COLUMN weight_kg current_weight DECIMAL(6,2) NULL COMMENT '当前体重kg';

UPDATE pets
SET gender = CASE gender
  WHEN '1' THEN 'male'
  WHEN '2' THEN 'female'
  WHEN '0' THEN 'unknown'
  WHEN '' THEN NULL
  ELSE gender
END;

ALTER TABLE pets
  ADD KEY idx_pets_category (category_id);
