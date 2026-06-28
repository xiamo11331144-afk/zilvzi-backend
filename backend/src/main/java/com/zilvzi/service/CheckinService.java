package com.zilvzi.service;

import com.zilvzi.pojo.Checkin;
import java.util.List;

/**
 * 打卡Service接口
 */
public interface CheckinService {

    /**
     * 打卡
     * @param userId 用户ID
     * @param goalId 目标ID（可选）
     * @param taskId 任务ID（可选）
     * @param content 内容
     * @param image 图片
     */
    Checkin doCheckin(Long userId, Long goalId, Long taskId, String content, String image, String proofImage, String proofContent);

    /**
     * 获取用户打卡日期列表
     */
    List<String> getCheckinDates(Long userId, Integer year, Integer month);

    /**
     * 获取今日任务
     */
    List<Checkin> getTodayTasks(Long userId);

    /**
     * 获取连续打卡天数
     */
    Integer getStreak(Long userId, Long goalId);

    /**
     * 获取总打卡天数
     */
    Integer getTotalCheckins(Long userId, Long goalId);

    List<Checkin> getRecentCheckins(Long userId, Integer limit);

    /**
     * 检查用户是否有断卡情况
     * @return 断卡信息，如果没有断卡返回 null
     */
    java.util.Map<String, Object> checkStreakBreak(Long userId);

    /**
     * 搭子审核打卡（先到先得）
     */
    Checkin reviewCheckin(Long userId, Long checkinId, boolean approved, String comment);

    /**
     * 获取当前用户需要审核的打卡列表
     */
    List<Checkin> getPendingForReview(Long userId);
}