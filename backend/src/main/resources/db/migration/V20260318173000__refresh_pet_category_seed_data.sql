CREATE TEMPORARY TABLE tmp_pet_category_mapping AS
SELECT
  p.id AS pet_id,
  CASE pc.code
    WHEN 'bird_song' THEN 'bird_songbird'
    ELSE pc.code
  END AS target_code
FROM pets p
LEFT JOIN pet_categories pc ON pc.id = p.category_id;

DELETE FROM pet_categories;

INSERT INTO pet_categories (id, name, code, parent_id, level_num, path, sort_num) VALUES
  (1, '猫类', 'cat', NULL, 1, 'cat', 1),
  (2, '狗类', 'dog', NULL, 1, 'dog', 2),
  (3, '鸟类', 'bird', NULL, 1, 'bird', 3),
  (4, '啮齿类', 'rodent', NULL, 1, 'rodent', 4),
  (5, '兔类', 'rabbit', NULL, 1, 'rabbit', 5),
  (6, '雪貂类', 'ferret', NULL, 1, 'ferret', 6),
  (7, '爬宠', 'reptile', NULL, 1, 'reptile', 7),
  (8, '两栖', 'amphibian', NULL, 1, 'amphibian', 8),
  (9, '水族', 'fish', NULL, 1, 'fish', 9),
  (10, '龟鳖类', 'turtle', NULL, 1, 'turtle', 10),
  (11, '节肢/无脊椎', 'invertebrate', NULL, 1, 'invertebrate', 11),
  (12, '其他', 'other', NULL, 1, 'other', 12),

  (101, '短毛猫', 'cat_short', 1, 2, 'cat/cat_short', 1),
  (102, '长毛猫', 'cat_long', 1, 2, 'cat/cat_long', 2),
  (103, '无毛猫', 'cat_hairless', 1, 2, 'cat/cat_hairless', 3),

  (201, '小型犬', 'dog_small', 2, 2, 'dog/dog_small', 1),
  (202, '中型犬', 'dog_medium', 2, 2, 'dog/dog_medium', 2),
  (203, '大型犬', 'dog_large', 2, 2, 'dog/dog_large', 3),

  (301, '鹦鹉', 'bird_parrot', 3, 2, 'bird/parrot', 1),
  (302, '鸣禽', 'bird_songbird', 3, 2, 'bird/songbird', 2),
  (303, '鸽类', 'bird_pigeon', 3, 2, 'bird/pigeon', 3),
  (304, '鸡形观赏鸟', 'bird_galliformes', 3, 2, 'bird/galliformes', 4),

  (401, '仓鼠', 'rodent_hamster', 4, 2, 'rodent/hamster', 1),
  (402, '鼠类', 'rodent_rat', 4, 2, 'rodent/rat', 2),
  (403, '豚鼠类', 'rodent_guinea_pig', 4, 2, 'rodent/guinea_pig', 3),
  (404, '龙猫类', 'rodent_chinchilla', 4, 2, 'rodent/chinchilla', 4),
  (405, '松鼠类', 'rodent_squirrel', 4, 2, 'rodent/squirrel', 5),

  (501, '宠物兔', 'rabbit_pet', 5, 2, 'rabbit/pet', 1),

  (601, '雪貂', 'ferret_domestic', 6, 2, 'ferret/domestic', 1),

  (701, '守宫', 'reptile_gecko', 7, 2, 'reptile/gecko', 1),
  (702, '蜥蜴', 'reptile_lizard', 7, 2, 'reptile/lizard', 2),
  (703, '蛇类', 'reptile_snake', 7, 2, 'reptile/snake', 3),

  (801, '蛙类', 'amphibian_frog', 8, 2, 'amphibian/frog', 1),
  (802, '蝾螈类', 'amphibian_salamander', 8, 2, 'amphibian/salamander', 2),

  (901, '观赏鱼', 'fish_aqua', 9, 2, 'fish/aqua', 1),
  (902, '观赏虾蟹', 'fish_shrimp_crab', 9, 2, 'fish/shrimp_crab', 2),

  (1001, '水龟', 'turtle_aquatic', 10, 2, 'turtle/aquatic', 1),
  (1002, '陆龟', 'turtle_terrestrial', 10, 2, 'turtle/terrestrial', 2),

  (1101, '蜘蛛类', 'invertebrate_spider', 11, 2, 'invertebrate/spider', 1),
  (1102, '昆虫类', 'invertebrate_insect', 11, 2, 'invertebrate/insect', 2),
  (1103, '螳螂类', 'invertebrate_mantis', 11, 2, 'invertebrate/mantis', 3),

  (1201, '其他宠物', 'other_pet', 12, 2, 'other/pet', 1),

  -- 这里把猫类三级分类改到 1301-1306，避免与龟鳖类二级分类 1001/1002 的主键冲突。
  (1301, '英国短毛猫', 'british_shorthair', 101, 3, 'cat/cat_short/british_shorthair', 1),
  (1302, '美国短毛猫', 'american_shorthair', 101, 3, 'cat/cat_short/american_shorthair', 2),
  (1303, '中华田园猫', 'chinese_domestic_cat', 101, 3, 'cat/cat_short/chinese_domestic_cat', 3),
  (1304, '布偶猫', 'ragdoll', 102, 3, 'cat/cat_long/ragdoll', 1),
  (1305, '缅因猫', 'maine_coon', 102, 3, 'cat/cat_long/maine_coon', 2),
  (1306, '斯芬克斯猫', 'sphynx_cat', 103, 3, 'cat/cat_hairless/sphynx_cat', 1),

  (2001, '柯基', 'corgi', 201, 3, 'dog/dog_small/corgi', 1),
  (2002, '博美', 'pomeranian', 201, 3, 'dog/dog_small/pomeranian', 2),
  (2003, '泰迪/贵宾', 'poodle_toy', 201, 3, 'dog/dog_small/poodle_toy', 3),
  (2004, '柴犬', 'shiba_inu', 202, 3, 'dog/dog_medium/shiba_inu', 1),
  (2005, '边境牧羊犬', 'border_collie', 202, 3, 'dog/dog_medium/border_collie', 2),
  (2006, '拉布拉多', 'labrador', 203, 3, 'dog/dog_large/labrador', 1),
  (2007, '金毛', 'golden_retriever', 203, 3, 'dog/dog_large/golden_retriever', 2),
  (2008, '阿拉斯加', 'alaskan_malamute', 203, 3, 'dog/dog_large/alaskan_malamute', 3),

  (3001, '玄凤鹦鹉', 'cockatiel', 301, 3, 'bird/parrot/cockatiel', 1),
  (3002, '虎皮鹦鹉', 'budgerigar', 301, 3, 'bird/parrot/budgerigar', 2),
  (3003, '牡丹鹦鹉', 'lovebird', 301, 3, 'bird/parrot/lovebird', 3),
  (3004, '和尚鹦鹉', 'monk_parakeet', 301, 3, 'bird/parrot/monk_parakeet', 4),
  (3005, '金太阳鹦鹉', 'sun_conure', 301, 3, 'bird/parrot/sun_conure', 5),
  (3006, '文鸟', 'java_sparrow', 302, 3, 'bird/songbird/java_sparrow', 1),
  (3007, '金丝雀', 'canary', 302, 3, 'bird/songbird/canary', 2),
  (3008, '观赏鸽', 'fancy_pigeon', 303, 3, 'bird/pigeon/fancy_pigeon', 1),
  (3009, '鹌鹑', 'quail', 304, 3, 'bird/galliformes/quail', 1),

  (4001, '金丝熊', 'syrian_hamster', 401, 3, 'rodent/hamster/syrian_hamster', 1),
  (4002, '三线仓鼠', 'djungarian_hamster', 401, 3, 'rodent/hamster/djungarian_hamster', 2),
  (4003, '一线仓鼠', 'campbells_hamster', 401, 3, 'rodent/hamster/campbells_hamster', 3),
  (4004, '罗伯罗夫斯基仓鼠', 'roborovski_hamster', 401, 3, 'rodent/hamster/roborovski_hamster', 4),
  (4005, '花枝鼠', 'fancy_rat', 402, 3, 'rodent/rat/fancy_rat', 1),
  (4006, '沙鼠', 'gerbil', 402, 3, 'rodent/rat/gerbil', 2),
  (4007, '豚鼠', 'guinea_pig', 403, 3, 'rodent/guinea_pig/guinea_pig', 1),
  (4008, '龙猫', 'chinchilla', 404, 3, 'rodent/chinchilla/chinchilla', 1),
  (4009, '蜜袋鼯', 'sugar_glider', 405, 3, 'rodent/squirrel/sugar_glider', 1),

  (5001, '垂耳兔', 'lop_rabbit', 501, 3, 'rabbit/pet/lop_rabbit', 1),
  (5002, '侏儒兔', 'netherland_dwarf', 501, 3, 'rabbit/pet/netherland_dwarf', 2),

  (6001, '家养雪貂', 'domestic_ferret', 601, 3, 'ferret/domestic/domestic_ferret', 1),

  (7001, '豹纹守宫', 'leopard_gecko', 701, 3, 'reptile/gecko/leopard_gecko', 1),
  (7002, '肥尾守宫', 'fat_tail_gecko', 701, 3, 'reptile/gecko/fat_tail_gecko', 2),
  (7003, '鬃狮蜥', 'bearded_dragon', 702, 3, 'reptile/lizard/bearded_dragon', 1),
  (7004, '绿鬣蜥', 'green_iguana', 702, 3, 'reptile/lizard/green_iguana', 2),
  (7005, '玉米蛇', 'corn_snake', 703, 3, 'reptile/snake/corn_snake', 1),
  (7006, '球蟒', 'ball_python', 703, 3, 'reptile/snake/ball_python', 2),

  (8001, '角蛙', 'pacman_frog', 801, 3, 'amphibian/frog/pacman_frog', 1),
  (8002, '树蛙', 'tree_frog', 801, 3, 'amphibian/frog/tree_frog', 2),
  (8003, '六角恐龙', 'axolotl', 802, 3, 'amphibian/salamander/axolotl', 1),

  (9001, '金鱼', 'goldfish', 901, 3, 'fish/aqua/goldfish', 1),
  (9002, '孔雀鱼', 'guppy', 901, 3, 'fish/aqua/guppy', 2),
  (9003, '斗鱼', 'betta', 901, 3, 'fish/aqua/betta', 3),
  (9004, '锦鲤', 'koi', 901, 3, 'fish/aqua/koi', 4),
  (9005, '观赏虾', 'ornamental_shrimp', 902, 3, 'fish/shrimp_crab/ornamental_shrimp', 1),
  (9006, '寄居蟹', 'hermit_crab', 902, 3, 'fish/shrimp_crab/hermit_crab', 2),

  (10001, '巴西龟', 'red_eared_slider', 1001, 3, 'turtle/aquatic/red_eared_slider', 1),
  (10002, '麝香龟', 'musk_turtle', 1001, 3, 'turtle/aquatic/musk_turtle', 2),
  (10003, '苏卡达陆龟', 'sulcata_tortoise', 1002, 3, 'turtle/terrestrial/sulcata_tortoise', 1),

  (11001, '捕鸟蛛', 'tarantula', 1101, 3, 'invertebrate/spider/tarantula', 1),
  (11002, '独角仙', 'rhinoceros_beetle', 1102, 3, 'invertebrate/insect/rhinoceros_beetle', 1),
  (11003, '竹节虫', 'stick_insect', 1102, 3, 'invertebrate/insect/stick_insect', 2),
  (11004, '兰花螳螂', 'orchid_mantis', 1103, 3, 'invertebrate/mantis/orchid_mantis', 1),

  (12001, '其他未分类宠物', 'other_uncategorized_pet', 1201, 3, 'other/pet/uncategorized', 1);

UPDATE pets p
LEFT JOIN tmp_pet_category_mapping m ON m.pet_id = p.id
LEFT JOIN pet_categories c ON c.code = m.target_code
SET
  p.category_id = c.id,
  p.category_path = c.path,
  p.breed = CASE
    WHEN c.id IS NULL THEN p.breed
    WHEN p.custom_species_note IS NULL OR TRIM(p.custom_species_note) = '' THEN c.name
    ELSE CONCAT(c.name, '（', p.custom_species_note, '）')
  END
WHERE p.category_id IS NOT NULL OR p.category_path IS NOT NULL;

DROP TEMPORARY TABLE tmp_pet_category_mapping;
