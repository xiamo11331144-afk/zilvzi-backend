-- 仅清理 mentor_test_user_11_seed.sql 标记的测试打卡。
-- 建议先在小程序关闭11号账号的引领开关，再执行本文件。
-- 不修改账号密码、等级、经验和开关，不删除已有业务打卡。
-- 测试目标保留并设为 abandoned，避免删除目标导致关联数据级联删除。
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @mentor_test_user = 11;
SET @mentor_test_marker = '__MENTOR_MATCH_TEST_USER_11_V1__';
START TRANSACTION;

DELETE c FROM `checkin` c
INNER JOIN `goal` g ON g.id = c.goal_id AND g.user_id = c.user_id
WHERE c.user_id = @mentor_test_user
  AND g.description = @mentor_test_marker
  AND c.content = @mentor_test_marker
  AND c.task_id IS NULL;

UPDATE `goal` g
SET g.status = 'abandoned', g.streak = 0,
    g.total_checkins = (
        SELECT COUNT(DISTINCT c.date) FROM `checkin` c
        WHERE c.goal_id = g.id AND c.user_id = @mentor_test_user AND c.status = 'approved'
    ),
    g.update_time = NOW()
WHERE g.user_id = @mentor_test_user AND g.description = @mentor_test_marker;

COMMIT;

SELECT COUNT(*) AS remainingTestCheckins
FROM `checkin` c
INNER JOIN `goal` g ON g.id = c.goal_id AND g.user_id = c.user_id
WHERE c.user_id = @mentor_test_user AND g.description = @mentor_test_marker
  AND c.content = @mentor_test_marker AND c.task_id IS NULL;
