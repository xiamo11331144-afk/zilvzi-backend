package com.zilvzi.mapper;

import com.zilvzi.pojo.Checkin;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDate;
import java.util.List;

/**
 * 打卡记录Mapper接口
 */
public interface CheckinMapper {

    /**
     * 根据ID查询打卡记录
     */
    Checkin selectById(@Param("id") Long id);

    /**
     * 查询用户某日是否已打卡
     */
    Checkin selectByUserAndDate(@Param("userId") Long userId, @Param("goalId") Long goalId, @Param("date") String date);

    Checkin selectByTaskAndDate(@Param("taskId") Long taskId, @Param("date") String date);

    /**
     * 查询用户的打卡日期列表
     */
    List<String> selectCheckinDates(@Param("userId") Long userId, @Param("year") Integer year, @Param("month") Integer month);

    /**
     * 查询用户今日任务列表
     */
    List<Checkin> selectTodayTasks(@Param("userId") Long userId, @Param("today") LocalDate today);

    /**
     * 插入打卡记录
     */
    int insert(Checkin checkin);

    /**
     * 统计连续打卡天数
     */
    Integer countStreak(@Param("userId") Long userId, @Param("goalId") Long goalId, @Param("today") LocalDate today);

    /**
     * 统计总打卡天数
     */
    Integer countTotal(@Param("userId") Long userId, @Param("goalId") Long goalId);

    /**
     * 统计用户所有目标的总打卡天数
     */
    Integer countTotalCheckins(@Param("userId") Long userId);

    /**
     * 统计今日打卡数
     */
    Integer countToday(@Param("today") LocalDate today);

    List<Checkin> selectRecentByUserId(@Param("userId") Long userId, @Param("limit") Integer limit);

    /**
     * 更新打卡审核状态
     */
    int resubmitRejected(Checkin checkin);

    int updateStatus(@Param("id") Long id, @Param("status") String status,
                     @Param("partnerId") Long partnerId, @Param("reviewComment") String reviewComment);

    /**
     * 查询某目标下待审核的打卡（供搭子查看）
     */
    List<Checkin> selectPendingByGoalId(@Param("goalId") Long goalId);

    /**
     * 查询当前用户所有搭子目标下待审核的打卡（排除自己的）
     */
    List<Checkin> selectPendingForReview(@Param("userId") Long userId);

    /**
     * 查询用户最早的打卡日期
     */
    String selectEarliestDate(@Param("userId") Long userId);
}