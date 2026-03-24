CREATE TABLE pet_weight_records (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  pet_id BIGINT NOT NULL COMMENT '关联宠物ID',
  user_id BIGINT NOT NULL COMMENT '记录所属用户ID，便于按用户快速校验归属',
  weight_value DECIMAL(6,2) NOT NULL COMMENT '体重数值',
  unit VARCHAR(10) NOT NULL DEFAULT 'kg' COMMENT '体重单位，当前阶段固定为kg',
  source VARCHAR(20) NULL COMMENT '记录来源：home/clinic/other',
  note VARCHAR(255) NULL COMMENT '备注说明',
  recorded_at DATETIME NOT NULL COMMENT '实际测量时间',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_pet_weight_pet_time (pet_id, recorded_at),
  INDEX idx_pet_weight_user_time (user_id, recorded_at)
);
