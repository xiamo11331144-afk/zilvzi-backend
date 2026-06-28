package com.zilvzi.controller;

import com.zilvzi.mapper.PlanMapper;
import com.zilvzi.mapper.CheckinMapper;
import com.zilvzi.pojo.Plan;
import com.zilvzi.pojo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    @Autowired
    private PlanMapper planMapper;

    @Autowired
    private CheckinMapper checkinMapper;

    @GetMapping("/dashboard")
    public Result<?> getDashboard(@RequestAttribute("userId") Long userId) {
        try {
            Map<String, Object> stats = buildStats(userId);
            return Result.success(stats);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/user/{userId}")
    public Result<?> getUserStats(@RequestAttribute("userId") Long currentUserId, @PathVariable Long userId) {
        try {
            // 校验只能查看自己的统计
            if (!currentUserId.equals(userId)) {
                return Result.error(403, "无权查看其他用户的统计");
            }
            Map<String, Object> stats = buildStats(userId);
            return Result.success(stats);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    private Map<String, Object> buildStats(Long userId) {
        Map<String, Object> stats = new HashMap<>();
        List<Plan> plans = planMapper.selectByUserId(userId);
        
        int totalDays = 0;
        int completedDays = 0;
        for (Plan p : plans) {
            if (p.getTotalDays() != null) totalDays += p.getTotalDays();
            if (p.getCompletedDays() != null) completedDays += p.getCompletedDays();
        }
        
        Integer streak = checkinMapper.countStreak(userId, null, java.time.LocalDate.now());
        
        stats.put("totalCheckins", completedDays);
        stats.put("streakDays", streak != null ? streak : 0);
        stats.put("completionRate", totalDays > 0 
            ? Math.round((float) completedDays / totalDays * 100) : 0);
        stats.put("totalDays", totalDays);
        return stats;
    }
}