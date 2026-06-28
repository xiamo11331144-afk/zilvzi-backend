package com.zilvzi.controller;

import com.zilvzi.pojo.Achievement;
import com.zilvzi.pojo.Result;
import com.zilvzi.service.AchievementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 成就Controller
 */
@RestController
@RequestMapping("/api/achievement")
public class AchievementController {

    @Autowired
    private AchievementService achievementService;

    /**
     * 获取用户成就列表
     */
    @GetMapping("/list")
    public Result<?> getAchievements(@RequestAttribute("userId") Long userId) {
        try {
            List<Achievement> achievements = achievementService.getUserAchievements(userId);
            return Result.success(achievements);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取用户等级信息
     */
    @GetMapping("/level")
    public Result<?> getLevelInfo(@RequestAttribute("userId") Long userId) {
        try {
            Map<String, Object> levelInfo = achievementService.getUserLevelInfo(userId);
            return Result.success(levelInfo);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取排行榜
     */
    @GetMapping("/ranking")
    public Result<?> getRanking(@RequestParam(defaultValue = "10") Integer limit) {
        try {
            List<Map<String, Object>> ranking = achievementService.getRankingList(limit);
            return Result.success(ranking);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}