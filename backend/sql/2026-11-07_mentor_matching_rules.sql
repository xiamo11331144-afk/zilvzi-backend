-- 自愿引领匹配第二阶段；在当前业务库执行，支持重复执行。
-- 旧关系统一保留为 mutual，不根据等级或现有开关猜测历史模式。
-- 本脚本只增加字段/索引，不删除或改写已有业务记录。
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

SET @mentor_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'user' AND column_name = 'mentor_enabled') = 0,
    'ALTER TABLE `user` ADD COLUMN `mentor_enabled` tinyint NOT NULL DEFAULT 0 COMMENT ''是否自愿开启引领匹配'' AFTER `role`',
    'SELECT ''mentor_enabled already exists'' AS migration_info');
PREPARE mentor_stmt FROM @mentor_ddl;
EXECUTE mentor_stmt;
DEALLOCATE PREPARE mentor_stmt;

SET @mentor_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'partner' AND column_name = 'match_mode') = 0,
    'ALTER TABLE `partner` ADD COLUMN `match_mode` varchar(10) NOT NULL DEFAULT ''mutual'' COMMENT ''mutual共勉；lead接收方引领申请方'' AFTER `goal_id`',
    'SELECT ''match_mode already exists'' AS migration_info');
PREPARE mentor_stmt FROM @mentor_ddl;
EXECUTE mentor_stmt;
DEALLOCATE PREPARE mentor_stmt;

SET @mentor_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'partner' AND index_name = 'idx_mentor_capacity') = 0,
    'ALTER TABLE `partner` ADD INDEX `idx_mentor_capacity` (`partner_id`, `match_mode`, `status`)',
    'SELECT ''idx_mentor_capacity already exists'' AS migration_info');
PREPARE mentor_stmt FROM @mentor_ddl;
EXECUTE mentor_stmt;
DEALLOCATE PREPARE mentor_stmt;

SET @mentor_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'checkin' AND index_name = 'idx_mentor_activity') = 0,
    'ALTER TABLE `checkin` ADD INDEX `idx_mentor_activity` (`status`, `user_id`, `date`)',
    'SELECT ''idx_mentor_activity already exists'' AS migration_info');
PREPARE mentor_stmt FROM @mentor_ddl;
EXECUTE mentor_stmt;
DEALLOCATE PREPARE mentor_stmt;
