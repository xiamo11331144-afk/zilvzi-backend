-- 认证引领者匹配：为用户增加独立的引领开关
ALTER TABLE `user`
  ADD COLUMN `mentor_enabled` tinyint NOT NULL DEFAULT 0 COMMENT '是否开启认证引领者匹配: 0-否 1-是' AFTER `role`;
