package com.zilvzi.controller;

import com.zilvzi.pojo.Checkin;
import com.zilvzi.pojo.Result;
import com.zilvzi.service.CheckinService;
import com.zilvzi.service.PlanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 打卡Controller
 */
@RestController
@RequestMapping("/api/checkin")
public class CheckinController {

    @Autowired
    private CheckinService checkinService;
    
    @Autowired
    private PlanService planService;

    /**
     * 打卡
     */
    @PostMapping("/do")
    public Result<?> doCheckin(@RequestAttribute("userId") Long userId, @RequestBody Map<String, Object> params) {
        try {
            // 支持两种打卡方式：1. goalId 2. taskId
            Long goalId = params.get("goalId") != null ? Long.valueOf(params.get("goalId").toString()) : null;
            Long taskId = params.get("taskId") != null ? Long.valueOf(params.get("taskId").toString()) : null;
            String content = (String) params.get("content");
            String image = (String) params.get("image");
            String proofImage = (String) params.get("proofImage");
            String proofContent = (String) params.get("proofContent");

            System.out.println("[Checkin] ====== 打卡请求 ======");
            System.out.println("[Checkin] userId=" + userId);
            System.out.println("[Checkin] goalId=" + goalId + ", taskId=" + taskId);
            System.out.println("[Checkin] content=" + content);
            System.out.println("[Checkin] image=" + image);
            System.out.println("[Checkin] proofImage=" + proofImage);
            System.out.println("[Checkin] proofContent=" + proofContent);
            System.out.println("[Checkin] params全部=" + params);

            Checkin checkin = checkinService.doCheckin(userId, goalId, taskId, content, image, proofImage, proofContent);
            System.out.println("[Checkin] ✅ 打卡完成, checkinId=" + checkin.getId() + ", status=" + checkin.getStatus());
            return Result.success("打卡成功", checkin);
        } catch (Exception e) {
            System.out.println("[Checkin] ❌ 打卡失败: " + e.getMessage());
            e.printStackTrace();
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取打卡日期列表
     */
    @GetMapping("/dates")
    public Result<?> getCheckinDates(@RequestAttribute("userId") Long userId,
                                     @RequestParam Integer year,
                                     @RequestParam Integer month) {
        try {
            List<String> dates = checkinService.getCheckinDates(userId, year, month);
            return Result.success(dates);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取今日任务
     */
    @GetMapping("/today")
    public Result<?> getTodayTasks(@RequestAttribute("userId") Long userId) {
        try {
            List<Checkin> tasks = checkinService.getTodayTasks(userId);
            return Result.success(tasks);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取打卡统计
     */
    @GetMapping("/stats")
    public Result<?> getCheckinStats(@RequestAttribute("userId") Long userId,
                                     @RequestParam(required = false) Long goalId) {
        try {
            Integer streak = checkinService.getStreak(userId, goalId);
            Integer total = checkinService.getTotalCheckins(userId, goalId);

            Map<String, Object> stats = new HashMap<>();
            stats.put("streak", streak);
            stats.put("total", total);

            return Result.success(stats);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取指定用户最近打卡记录
     */
    @GetMapping("/user/{userId}")
    public Result<?> getUserCheckins(@RequestAttribute("userId") Long currentUserId, @PathVariable Long userId) {
        try {
            // 校验只能查看自己的打卡记录
            if (!currentUserId.equals(userId)) {
                return Result.error(403, "无权查看其他用户的打卡记录");
            }
            List<Checkin> records = checkinService.getRecentCheckins(userId, 10);
            return Result.success(records);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 检查用户是否有断卡情况，自动触发补卡调整
     * 前端进入小程序时调用，会自动标记断卡 + 创建补发任务
     * 使用 POST 因为该接口有副作用（标记断卡、创建补发任务、清理脏数据）
     */
    @PostMapping("/streak-break/check")
    public Result<?> checkStreakBreak(@RequestAttribute("userId") Long userId) {
        try {
            Map<String, Object> streakBreakInfo = checkinService.checkStreakBreak(userId);
            
            if (streakBreakInfo == null) {
                return Result.success("无活跃计划", Map.of("hasStreakBreak", false));
            }
            
            boolean hasStreakBreak = (boolean) streakBreakInfo.get("hasStreakBreak");
            if (hasStreakBreak) {
                return Result.success("检测到断卡，已自动调整计划", streakBreakInfo);
            }
            
            return Result.success("无断卡情况", streakBreakInfo);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 搭子审核打卡（先到先得）
     */
    @PostMapping("/review")
    public Result<?> reviewCheckin(@RequestAttribute("userId") Long userId, @RequestBody Map<String, Object> params) {
        try {
            Long checkinId = Long.valueOf(params.get("checkinId").toString());
            boolean approved = Boolean.TRUE.equals(params.get("approved"));
            String comment = (String) params.get("comment");
            Checkin checkin = checkinService.reviewCheckin(userId, checkinId, approved, comment);
            return Result.success(approved ? "审核通过" : "已驳回", checkin);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取待审核打卡列表（搭子需要审核的）
     */
    @GetMapping("/pending")
    public Result<?> getPendingReviews(@RequestAttribute("userId") Long userId) {
        try {
            List<Checkin> list = checkinService.getPendingForReview(userId);
            return Result.success(list);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}