package com.zilvzi.service;

import com.zilvzi.pojo.Goal;
import java.util.List;

/**
 * 目标Service接口
 */
public interface GoalService {

    Goal createGoal(Goal goal);
    Goal getGoalById(Long id);

    /**
     * 获取用户目标列表
     */
    List<Goal> getUserGoals(Long userId);

    /**
     * 获取今日目标
     */
    List<Goal> getTodayGoals(Long userId);

    /**
     * 更新目标
     */
    void updateGoal(Goal goal);

    /**
     * 删除目标
     */
    void deleteGoal(Long id);

    /**
     * 完成目标
     */
    void completeGoal(Long id);

    /**
     * 放弃目标
     */
    void abandonGoal(Long id);
}