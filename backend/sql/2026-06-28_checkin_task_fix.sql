-- 修复同一目标同一天多个计划任务打卡时的唯一索引冲突。
-- 适用于已经导入过旧版 zilvzi.sql 的数据库。
-- 执行前请先选中数据库，例如：USE zilvzi;

SET @db_name = DATABASE();

-- 1. 旧脚本没有 task_id 字段时补充字段。
SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE `checkin` ADD COLUMN `task_id` bigint NULL DEFAULT NULL COMMENT ''计划任务ID'' AFTER `goal_id`',
    'SELECT ''task_id already exists'' AS info'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db_name
    AND TABLE_NAME = 'checkin'
    AND COLUMN_NAME = 'task_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 移除旧的“用户+目标+日期”唯一索引，避免同一天多个任务互相冲突。
SET @sql = (
  SELECT IF(
    COUNT(*) > 0,
    'ALTER TABLE `checkin` DROP INDEX `uk_user_goal_date`',
    'SELECT ''uk_user_goal_date not found'' AS info'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = @db_name
    AND TABLE_NAME = 'checkin'
    AND INDEX_NAME = 'uk_user_goal_date'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. 普通目标打卡仍通过代码按 user_id + goal_id + date 判重，这里保留查询索引。
SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'CREATE INDEX `idx_user_goal_date` ON `checkin` (`user_id`, `goal_id`, `date`)',
    'SELECT ''idx_user_goal_date already exists'' AS info'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = @db_name
    AND TABLE_NAME = 'checkin'
    AND INDEX_NAME = 'idx_user_goal_date'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4. 任务打卡按 task_id + date 判重，允许同一目标同一天完成多个不同任务。
SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'CREATE UNIQUE INDEX `uk_task_date` ON `checkin` (`task_id`, `date`)',
    'SELECT ''uk_task_date already exists'' AS info'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = @db_name
    AND TABLE_NAME = 'checkin'
    AND INDEX_NAME = 'uk_task_date'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'CREATE INDEX `idx_task_id` ON `checkin` (`task_id`)',
    'SELECT ''idx_task_id already exists'' AS info'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = @db_name
    AND TABLE_NAME = 'checkin'
    AND INDEX_NAME = 'idx_task_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
