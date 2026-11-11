-- 修复旧版在搭子审核前就把任务计为完成的历史记录。
-- 手工部署步骤：备份数据库，暂停打卡/审核写入，选择业务数据库后执行。
-- 依赖 2026-06-28_checkin_task_fix.sql 已提供的 checkin.task_id。
-- 仅处理存在 pending/rejected 且不存在 approved 打卡的 completed=1 任务。
-- 已通过的任务、没有打卡记录的历史完成任务保持原样。可重复执行。
-- 不撤销已经发放的历史成就；成就追溯需另行审查。

CREATE TEMPORARY TABLE checkin_completion_repair_tasks AS
SELECT DISTINCT t.id AS task_id, t.plan_id
FROM plan_task t
JOIN checkin c ON c.task_id = t.id AND c.status IN ('pending', 'rejected')
LEFT JOIN checkin approved ON approved.task_id = t.id AND approved.status = 'approved'
WHERE t.completed = 1 AND approved.id IS NULL;

-- 执行前审阅影响范围；如不符合预期，请停止并关闭本数据库会话。
SELECT COUNT(*) AS affected_tasks, COUNT(DISTINCT plan_id) AS affected_plans
FROM checkin_completion_repair_tasks;

START TRANSACTION;
UPDATE plan_task t
JOIN checkin_completion_repair_tasks repair ON repair.task_id = t.id
SET t.completed = 0
WHERE t.completed = 1;

-- 与 PlanTaskMapper.countCompletedDays 保持相同的按天统计和补发排除规则。
UPDATE plan p
JOIN (SELECT DISTINCT plan_id FROM checkin_completion_repair_tasks) affected ON affected.plan_id = p.id
LEFT JOIN (
    SELECT plan_id, COUNT(DISTINCT day_number) AS completed_days
    FROM plan_task
    WHERE completed = 1 AND task_name NOT LIKE '【补】%'
    GROUP BY plan_id
) counts ON counts.plan_id = p.id
SET p.completed_days = COALESCE(counts.completed_days, 0), p.update_time = NOW();
COMMIT;

DROP TEMPORARY TABLE checkin_completion_repair_tasks;
