-- 仅用于开发/测试数据库。测试账号固定为 user.id = 11。
-- 执行整个文件；按数据库当前日期生成最近24天数据，可重复执行，不修改已有打卡。
-- 不自动开启 mentor_enabled，请通过小程序开关验证开启接口。
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @mentor_test_user = 11;
SET @mentor_test_marker = '__MENTOR_MATCH_TEST_USER_11_V1__';
START TRANSACTION;

INSERT INTO `goal` (`user_id`, `name`, `type`, `description`, `start_date`, `end_date`, `status`, `streak`, `total_checkins`)
SELECT u.id, '引领匹配测试目标', '学习', @mentor_test_marker,
       DATE_SUB(CURDATE(), INTERVAL 23 DAY), DATE_ADD(CURDATE(), INTERVAL 30 DAY), 'active', 24, 24
FROM `user` u
WHERE u.id = @mentor_test_user
  AND NOT EXISTS (
      SELECT 1 FROM `goal` g WHERE g.user_id = u.id AND g.description = @mentor_test_marker
  );

SET @mentor_test_goal = (
    SELECT MIN(id) FROM `goal`
    WHERE user_id = @mentor_test_user AND description = @mentor_test_marker
);

-- 清理脚本会保留目标；再次执行本脚本可重新启用该测试目标。
UPDATE `goal`
SET `status` = 'active', `start_date` = DATE_SUB(CURDATE(), INTERVAL 23 DAY),
    `end_date` = DATE_ADD(CURDATE(), INTERVAL 30 DAY), `update_time` = NOW()
WHERE id = @mentor_test_goal AND user_id = @mentor_test_user AND description = @mentor_test_marker;

INSERT INTO `checkin` (`user_id`, `goal_id`, `task_id`, `content`, `checkin_time`, `date`, `status`)
SELECT @mentor_test_user, @mentor_test_goal, NULL, @mentor_test_marker,
       TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL days.n DAY), '08:00:00'),
       DATE_SUB(CURDATE(), INTERVAL days.n DAY), 'approved'
FROM (
    SELECT ones.n + tens.n * 10 AS n
    FROM (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
          UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) ones
    CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2) tens
) days
WHERE days.n < 24
  AND @mentor_test_goal IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM `checkin` c
      WHERE c.user_id = @mentor_test_user AND c.goal_id = @mentor_test_goal
        AND c.date = DATE_SUB(CURDATE(), INTERVAL days.n DAY)
        AND c.content = @mentor_test_marker
  );

COMMIT;

-- 预期 approvedDays >= 24、checkinRate >= 80.0；用户不存在时不会写入数据。
SELECT u.id AS userId, u.nickname, u.mentor_enabled AS mentorEnabled,
       COUNT(DISTINCT c.date) AS approvedDays,
       ROUND(COUNT(DISTINCT c.date) * 100.0 / 30, 1) AS checkinRate
FROM `user` u
LEFT JOIN `checkin` c ON c.user_id = u.id AND c.status = 'approved'
  AND c.date BETWEEN DATE_SUB(CURDATE(), INTERVAL 29 DAY) AND CURDATE()
WHERE u.id = @mentor_test_user
GROUP BY u.id, u.nickname, u.mentor_enabled;
