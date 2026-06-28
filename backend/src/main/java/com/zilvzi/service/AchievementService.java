package com.zilvzi.service;

import com.zilvzi.pojo.Achievement;
import java.util.List;
import java.util.Map;

/**
 * 成就Service接口
 */
public interface AchievementService {

    /**
     * 获取用户成就列表
     */
    List<Achievement> getUserAchievements(Long userId);

    /**
     * 获取用户等级信息
     */
    Map<String, Object> getUserLevelInfo(Long userId);

    /**
     * 获取排行榜
     */
    List<Map<String, Object>> getRankingList(Integer limit);

    /**
     * 检查并解锁成就
     */
    void checkAndUnlock(Long userId, String type);

    /**
     * 检查社交类勋章（搭子/圈子变化时调用）
     */
    void checkSocialAchievements(Long userId);
}