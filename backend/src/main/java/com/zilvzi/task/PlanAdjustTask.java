package com.zilvzi.task;

import com.zilvzi.mapper.PlanMapper;
import com.zilvzi.mapper.PlanTaskMapper;
import com.zilvzi.pojo.Plan;
import com.zilvzi.pojo.PlanTask;
import com.zilvzi.service.PlanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 定时任务：检测断卡并调整计划
 */
@Component
public class PlanAdjustTask {

    @Autowired
    private PlanMapper planMapper;

    @Autowired
    private PlanTaskMapper planTaskMapper;

    @Autowired
    private PlanService planService;

    /**
     * 每天凌晨1点检测断卡情况
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void checkAndAdjustPlans() {
        System.out.println("[Task] 开始检测断卡情况...");

        // 获取所有进行中的计划
        List<Plan> activePlans = planMapper.selectAllActive();

        for (Plan plan : activePlans) {
            try {
                checkPlanForStreakBreak(plan);
            } catch (Exception e) {
                System.out.println("[Task] 检测计划" + plan.getId() + "失败: " + e.getMessage());
            }
        }

        System.out.println("[Task] 断卡检测完成");
    }

    /**
     * 检测单个计划是否断卡（检查所有过期未完成任务，不只昨天）
     */
    private void checkPlanForStreakBreak(Plan plan) {
        // 检查所有过期未完成的原始任务（排除补发任务）
        List<PlanTask> missedTasks = planTaskMapper.selectIncompletePastDaysExcludeSupplement(plan.getId(), LocalDate.now());

        if (!missedTasks.isEmpty()) {
            System.out.println("[Task] 计划" + plan.getId() + " 检测到 " + missedTasks.size() + " 个过期未完成任务:");
            for (PlanTask t : missedTasks) {
                System.out.println("[Task]   id=" + t.getId() + " day=" + t.getDayNumber() + " name=" + t.getTaskName() + " completed=" + t.getCompleted() + " taskDate=" + t.getTaskDate());
            }
            planService.adjustPlan(plan.getId());
        } else {
            System.out.println("[Task] 计划" + plan.getId() + " 无断卡，跳过");
        }
    }
}