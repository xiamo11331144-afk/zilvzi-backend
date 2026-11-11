-- MySQL 8，只读诊断。请在小程序后端实际使用的数据库执行。
-- 固定核对：引领者 11、申请者 17；不包含密码、昵称或手机号。
-- 第一组显示每个硬性筛选条件；第二组显示名额和双向关系。
START TRANSACTION READ ONLY;

WITH approved_dates AS (
    SELECT DISTINCT user_id, date FROM checkin
    WHERE status = 'approved' AND date <= CURDATE() AND user_id IN (11, 17)
), ranked_dates AS (
    SELECT user_id, date,
           ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY date DESC) AS rn,
           MAX(date) OVER (PARTITION BY user_id) AS latest_date
    FROM approved_dates
), activity_stats AS (
    SELECT user_id,
           SUM(CASE WHEN date >= DATE_SUB(CURDATE(), INTERVAL 29 DAY) THEN 1 ELSE 0 END) AS approvedDays,
           ROUND(SUM(CASE WHEN date >= DATE_SUB(CURDATE(), INTERVAL 29 DAY) THEN 1 ELSE 0 END) * 100.0 / 30, 1) AS checkinRate,
           SUM(CASE WHEN latest_date >= DATE_SUB(CURDATE(), INTERVAL 1 DAY)
                    AND DATEDIFF(latest_date, date) = rn - 1 THEN 1 ELSE 0 END) AS streak
    FROM ranked_dates GROUP BY user_id
), occupied AS (
    SELECT COUNT(*) AS slots FROM partner
    WHERE partner_id = 11 AND match_mode = 'lead' AND status IN ('pending', 'accepted')
)
SELECT CURDATE() AS databaseDate,
       m.id AS mentorId, r.id AS requesterId, m.mentor_enabled AS mentorEnabled,
       COALESCE(a.approvedDays, 0) AS mentorApprovedDays,
       COALESCE(a.checkinRate, 0) AS mentorRate,
       COALESCE(a.streak, 0) AS mentorStreak,
       COALESCE(b.checkinRate, 0) AS requesterRate,
       o.slots AS occupiedSlots, GREATEST(3 - o.slots, 0) AS remainingSlots,
       (m.mentor_enabled = 1) AS passesEnabled,
       (COALESCE(a.approvedDays, 0) >= 24) AS passesApprovedDays,
       (COALESCE(a.checkinRate, 0) >= 80) AS passesRate,
       (COALESCE(a.streak, 0) >= 7) AS passesStreak,
       (COALESCE(a.checkinRate, 0) > COALESCE(b.checkinRate, 0)) AS passesStrictlyHigherRate,
       (o.slots < 3) AS passesCapacity,
       NOT EXISTS (
           SELECT 1 FROM partner p
           WHERE ((p.user_id = 17 AND p.partner_id = 11) OR (p.user_id = 11 AND p.partner_id = 17))
             AND p.status IN ('pending', 'accepted', 'blocked')
       ) AS passesPairExclusion
FROM user m CROSS JOIN user r CROSS JOIN occupied o
LEFT JOIN activity_stats a ON a.user_id = m.id
LEFT JOIN activity_stats b ON b.user_id = r.id
WHERE m.id = 11 AND r.id = 17;

SELECT user_id, partner_id, match_mode, status, COUNT(*) AS records
FROM partner
WHERE (partner_id = 11 AND match_mode = 'lead' AND status IN ('pending', 'accepted'))
   OR ((user_id = 17 AND partner_id = 11) OR (user_id = 11 AND partner_id = 17))
GROUP BY user_id, partner_id, match_mode, status;

SELECT user_id, type, status FROM goal WHERE user_id IN (11, 17);
ROLLBACK;
-- passes* 为 0 表示未满足对应筛选条件。
-- 全为 1 时还需要核对界面目标/关键词过滤、SQL前60名和服务推荐前20名。
-- 此脚本只诊断当前源码的筛选条件；未替代实际 /api/partner/match 调用。
