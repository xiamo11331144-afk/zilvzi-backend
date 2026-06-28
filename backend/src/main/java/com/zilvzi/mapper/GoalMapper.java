package com.zilvzi.mapper;

import com.zilvzi.pojo.Goal;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDate;
import java.util.List;

/**
 * 目标Mapper接口
 */
public interface GoalMapper {

    /**
     * 根据ID查询目标
     */
    Goal selectById(@Param("id") Long id);

    /**
     * 查询用户的目标列表
     */
    List<Goal> selectByUserId(@Param("userId") Long userId);

    /**
     * 查询用户今日待打卡目标
     */
    List<Goal> selectTodayGoals(@Param("userId") Long userId, @Param("today") LocalDate today);

    /**
     * 插入目标
     */
    int insert(Goal goal);

    /**
     * 更新目标
     */
    int update(Goal goal);

    /**
     * 更新目标状态
     */
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /**
     * 更新打卡统计
     */
    int updateCheckinStats(@Param("id") Long id, @Param("streak") Integer streak, @Param("totalCheckins") Integer totalCheckins);

    /**
     * 按 userId + name 查找活跃目标（防重复创建）
     */
    Goal selectActiveByName(@Param("userId") Long userId, @Param("name") String name);

    /**
     * 批量查询多个用户的活跃目标
     */
    List<Goal> selectGoalsByUserIds(@Param("userIds") List<Long> userIds);
}