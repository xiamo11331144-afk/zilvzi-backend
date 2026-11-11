package com.zilvzi.mapper;

import com.zilvzi.pojo.Plan;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 计划Mapper接口
 */
public interface PlanMapper {

    /**
     * 根据ID查询计划
     */
    Plan selectById(@Param("id") Long id);

    /** 阶段任务生成事务内锁定计划，串行化相同计划的首次生成。 */
    Plan selectByIdForUpdate(@Param("id") Long id);

    /**
     * 查询用户的计划列表
     */
    List<Plan> selectByUserId(@Param("userId") Long userId);

    /**
     * 查询目标关联的计划
     */
    Plan selectByGoalId(@Param("goalId") Long goalId);

    /**
     * 插入计划
     */
    int insert(Plan plan);

    /**
     * 更新计划
     */
    int update(Plan plan);

    /**
     * 更新完成天数
     */
    int updateCompletedDays(@Param("id") Long id, @Param("completedDays") Integer completedDays);

    /**
     * 删除计划
     */
    int deleteById(@Param("id") Long id);

    /**
     * 获取所有进行中的计划
     */
    List<Plan> selectAllActive();
}