package com.zilvzi.service;

import com.zilvzi.pojo.Plan;
import com.zilvzi.pojo.PlanTask;
import java.util.List;
import java.util.Map;

/**
 * 计划Service接口
 */
public interface PlanService {

    /**
     * AI生成计划（第一步：生成大纲）
     */
    Plan generatePlan(Long userId, Long goalId, Map<String, Object> params);

    /**
     * 生成某阶段的详细任务（第二步：按需生成）
     */
    List<PlanTask> generatePhaseTasks(Long planId, Integer phase);

    /**
     * 获取用户的计划列表
     */
    List<Plan> getUserPlans(Long userId);

    /**
     * 获取计划详情
     */
    Plan getPlanById(Long id);

    /**
     * 获取计划某天的任务
     */
    List<PlanTask> getDayTasks(Long planId, Integer dayNumber);

    /**
     * 获取计划的所有任务
     */
    List<PlanTask> getAllTasks(Long planId);

    /**
     * 完成任务
     */
    void completeTask(Long taskId);

    /**
     * 断卡后重新计算计划
     */
    void adjustPlan(Long planId);

    /**
     * 删除计划
     */
    void deletePlan(Long planId);
    void updatePlan(Plan plan);

    /**
     * 获取计划的断卡统计信息
     * 返回: streakBreakDays, streakBreakTasks, pendingDays
     */
    Map<String, Object> getStreakBreakInfo(Long planId);
}