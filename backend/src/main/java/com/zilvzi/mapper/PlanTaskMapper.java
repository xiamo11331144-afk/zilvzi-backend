package com.zilvzi.mapper;

import com.zilvzi.pojo.PlanTask;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDate;
import java.util.List;

/**
 * 计划任务Mapper接口
 */
public interface PlanTaskMapper {

    /**
     * 查询计划的每日任务
     */
    List<PlanTask> selectByPlanIdAndDay(@Param("planId") Long planId, @Param("dayNumber") Integer dayNumber);

    /**
     * 查询计划某天的任务
     */
    List<PlanTask> selectByPlanIdAndDate(@Param("planId") Long planId, @Param("taskDate") LocalDate taskDate);

    /**
     * 查询计划的所有任务
     */
    List<PlanTask> selectByPlanId(@Param("planId") Long planId);

    /**
     * 批量插入计划任务
     */
    int batchInsert(@Param("list") List<PlanTask> list);

    /**
     * 更新任务完成状态
     */
    int updateCompleted(@Param("id") Long id, @Param("completed") Integer completed);

    /**
     * 重置任务完成状态及图标颜色（用于断卡任务恢复打卡）
     */
    int resetTaskCompletion(@Param("id") Long id);

    /**
     * 更新任务断卡状态（同时更新 completed、图标、颜色）
     */
    int updateStreakBreakStatus(@Param("id") Long id, @Param("completed") Integer completed,
                                @Param("taskIcon") String taskIcon, @Param("taskColor") String taskColor);

    /**
     * 查询计划某阶段的任务（按天数范围）
     */
    List<PlanTask> selectByPlanIdAndPhase(@Param("planId") Long planId, @Param("startDay") Integer startDay, @Param("endDay") Integer endDay);

    /**
     * 查询计划某天范围的任务
     */
    List<PlanTask> selectByPlanIdAndDayRange(@Param("planId") Long planId, @Param("startDay") Integer startDay, @Param("endDay") Integer endDay);

    /**
     * 删除计划的所有任务
     */
    int deleteByPlanId(@Param("planId") Long planId);

    /**
     * 根据ID查询任务
     */
    PlanTask selectById(@Param("id") Long id);

    List<PlanTask> selectByUserId(@Param("userId") Long userId);

    List<PlanTask> selectCompletedByUserId(@Param("userId") Long userId);

    List<PlanTask> selectIncompletePastDays(@Param("planId") Long planId, @Param("today") LocalDate today);

    /**
     * 查询过期未完成的原始任务（排除补发任务）
     */
    List<PlanTask> selectIncompletePastDaysExcludeSupplement(@Param("planId") Long planId, @Param("today") LocalDate today);

    int singleInsert(PlanTask task);

    /**
     * 统计计划已完成的任务天数
     */
    Integer countCompletedDays(@Param("planId") Long planId);

    Integer countCompletedDaysByUserId(@Param("userId") Long userId);

    Integer countTotalTasksByUserId(@Param("userId") Long userId, @Param("today") LocalDate today);

    /**
     * 获取计划中未完成的任务
     */
    List<PlanTask> selectIncompleteByPlanId(@Param("planId") Long planId);

    /**
     * 删除计划中指定天数之后的任务
     */
    int deleteByPlanIdAndDayGreaterThanEqual(@Param("planId") Long planId, @Param("dayNumber") Integer dayNumber);

    /**
     * 查询计划中已有的补发任务（task_name 以【补】开头）
     */
    List<PlanTask> selectSupplementTasks(@Param("planId") Long planId);

    /**
     * 统计计划中已断卡的天数
     */
    Integer countStreakBreakDays(@Param("planId") Long planId);

    /**
     * 查询计划中断卡的任务详情
     */
    List<PlanTask> selectStreakBreakTasks(@Param("planId") Long planId);

    /**
     * 统计计划中过期未打卡的天数（task_date <= 今天且 completed = 0）
     */
    Integer countPendingDays(@Param("planId") Long planId, @Param("today") LocalDate today);

    /**
     * 删除过期的补发任务（task_date < 今天的补发任务）
     */
    int deleteExpiredSupplementTasks(@Param("planId") Long planId, @Param("today") LocalDate today);
}