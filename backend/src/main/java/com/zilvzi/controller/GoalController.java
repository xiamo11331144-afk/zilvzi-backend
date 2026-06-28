package com.zilvzi.controller;

import com.zilvzi.pojo.Goal;
import com.zilvzi.pojo.Result;
import com.zilvzi.service.GoalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 目标Controller
 */
@RestController
@RequestMapping("/api/goal")
public class GoalController {

    @Autowired
    private GoalService goalService;

    /**
     * 获取目标详情
     */
    @GetMapping("/detail/{id}")
    public Result<?> getGoalDetail(@RequestAttribute("userId") Long userId, @PathVariable Long id) {
        try {
            Goal goal = goalService.getGoalById(id);
            if (goal == null) return Result.error("目标不存在");
            // 校验目标归属
            if (!goal.getUserId().equals(userId)) {
                return Result.error(403, "无权查看此目标");
            }
            return Result.success(goal);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取今日目标
     */
    @PostMapping("/create")
    public Result<?> createGoal(@RequestAttribute("userId") Long userId, @RequestBody Goal goal) {
        try {
            goal.setUserId(userId);
            Goal created = goalService.createGoal(goal);
            return Result.success("创建成功", created);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取目标列表
     */
    @GetMapping("/list")
    public Result<?> getGoalList(@RequestAttribute("userId") Long userId) {
        try {
            System.out.println("[GoalController] 获取目标列表: userId=" + userId);
            List<Goal> goals = goalService.getUserGoals(userId);
            System.out.println("[GoalController] 返回" + goals.size() + "个目标");
            return Result.success(goals);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取指定用户的目标列表
     */
    @GetMapping("/user/{userId}")
    public Result<?> getGoalListByUserId(@RequestAttribute("userId") Long currentUserId, @PathVariable Long userId) {
        try {
            // 校验只能查看自己的目标
            if (!currentUserId.equals(userId)) {
                return Result.error(403, "无权查看其他用户的目标");
            }
            List<Goal> goals = goalService.getUserGoals(userId);
            return Result.success(goals);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
    @GetMapping("/today")
    public Result<?> getTodayGoals(@RequestAttribute("userId") Long userId) {
        try {
            List<Goal> goals = goalService.getTodayGoals(userId);
            return Result.success(goals);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 更新目标
     */
    @PutMapping("/update")
    public Result<?> updateGoal(@RequestAttribute("userId") Long userId, @RequestBody Goal goal) {
        try {
            // 校验目标归属
            Goal existingGoal = goalService.getGoalById(goal.getId());
            if (existingGoal == null || !existingGoal.getUserId().equals(userId)) {
                return Result.error(403, "无权修改此目标");
            }
            goalService.updateGoal(goal);
            return Result.success("更新成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 完成目标
     */
    @PutMapping("/complete/{id}")
    public Result<?> completeGoal(@RequestAttribute("userId") Long userId, @PathVariable Long id) {
        try {
            // 校验目标归属
            Goal goal = goalService.getGoalById(id);
            if (goal == null || !goal.getUserId().equals(userId)) {
                return Result.error(403, "无权操作此目标");
            }
            goalService.completeGoal(id);
            return Result.success("已完成");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 放弃目标
     */
    @PutMapping("/abandon/{id}")
    public Result<?> abandonGoal(@RequestAttribute("userId") Long userId, @PathVariable Long id) {
        try {
            // 校验目标归属
            Goal goal = goalService.getGoalById(id);
            if (goal == null || !goal.getUserId().equals(userId)) {
                return Result.error(403, "无权操作此目标");
            }
            goalService.abandonGoal(id);
            return Result.success("已放弃");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除目标
     */
    @DeleteMapping("/delete/{id}")
    public Result<?> deleteGoal(@RequestAttribute("userId") Long userId, @PathVariable Long id) {
        try {
            // 校验目标归属
            Goal goal = goalService.getGoalById(id);
            if (goal == null || !goal.getUserId().equals(userId)) {
                return Result.error(403, "无权删除此目标");
            }
            goalService.deleteGoal(id);
            return Result.success("删除成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}