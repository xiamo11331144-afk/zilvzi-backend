package com.zilvzi.controller;

import com.zilvzi.pojo.Plan;
import com.zilvzi.pojo.PlanTask;
import com.zilvzi.pojo.Result;
import com.zilvzi.service.PlanCacheService;
import com.zilvzi.service.PlanService;
import com.zilvzi.service.CheckinService;
import com.zilvzi.mapper.PlanTaskMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 计划Controller
 */
@RestController
@RequestMapping("/api/plan")
public class PlanController {

    @Autowired
    private PlanService planService;

    @Autowired
    private PlanCacheService planCacheService;

    @Autowired
    private PlanTaskMapper planTaskMapper;

    @Autowired
    private CheckinService checkinService;

    /**
     * AI生成计划
     */
    @PostMapping("/generate")
    public Result<?> generatePlan(@RequestAttribute("userId") Long userId, @RequestBody Map<String, Object> params) {
        try {
            Long goalId = null;
            if (params.get("goalId") != null) {
                goalId = Long.valueOf(params.get("goalId").toString());
            }
            Plan plan = planService.generatePlan(userId, goalId, params);
            return Result.success("计划生成成功", plan);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取计划列表（含断卡统计）
     */
    @GetMapping("/list")
    public Result<?> getPlanList(@RequestAttribute("userId") Long userId) {
        try {
            System.out.println("[PlanController] 获取计划列表: userId=" + userId);
            List<Plan> plans = planService.getUserPlans(userId);
            System.out.println("[PlanController] 查询到" + plans.size() + "个计划:");
            for (Plan plan : plans) {
                enrichPlan(plan);
                System.out.println("[PlanController]   → id=" + plan.getId()
                        + " name=" + plan.getName()
                        + " goalId=" + plan.getGoalId()
                        + " status=" + plan.getStatus()
                        + " totalDays=" + plan.getTotalDays()
                        + " completedDays=" + plan.getCompletedDays()
                        + " createTime=" + plan.getCreateTime());
            }
            return Result.success(plans);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取指定用户的计划列表（含断卡统计）
     */
    @GetMapping("/user/{userId}")
    public Result<?> getPlanListByUserId(@RequestAttribute("userId") Long currentUserId, @PathVariable Long userId) {
        try {
            // 校验只能查看自己的计划
            if (!currentUserId.equals(userId)) {
                return Result.error(403, "无权查看其他用户的计划");
            }
            List<Plan> plans = planService.getUserPlans(userId);
            for (Plan plan : plans) {
                enrichPlan(plan);
            }
            return Result.success(plans);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 更新计划
     */
    @PutMapping("/update")
    public Result<?> updatePlan(@RequestAttribute("userId") Long userId, @RequestBody Plan plan) {
        try {
            // 校验计划归属
            Plan existingPlan = planService.getPlanById(plan.getId());
            if (existingPlan == null || !existingPlan.getUserId().equals(userId)) {
                return Result.error(403, "无权修改此计划");
            }
            planService.updatePlan(plan);
            return Result.success("更新成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取计划详情（含断卡统计）
     */
    @GetMapping("/detail/{id}")
    public Result<?> getPlanDetail(@RequestAttribute("userId") Long userId, @PathVariable Long id) {
        try {
            Plan plan = planService.getPlanById(id);
            if (plan == null || !plan.getUserId().equals(userId)) {
                return Result.error(403, "无权查看此计划");
            }
            enrichPlan(plan);
            return Result.success(plan);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取某天的任务
     */
    @GetMapping("/{planId}/day/{dayNumber}")
    public Result<?> getDayTasks(@RequestAttribute("userId") Long userId, @PathVariable Long planId, @PathVariable Integer dayNumber) {
        try {
            Plan plan = planService.getPlanById(planId);
            if (plan == null || !plan.getUserId().equals(userId)) {
                return Result.error(403, "无权查看此计划");
            }
            List<PlanTask> tasks = planService.getDayTasks(planId, dayNumber);
            return Result.success(tasks);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取计划的所有任务
     */
    @GetMapping("/{planId}/tasks")
    public Result<?> getAllTasks(@RequestAttribute("userId") Long userId, @PathVariable Long planId) {
        try {
            Plan plan = planService.getPlanById(planId);
            if (plan == null || !plan.getUserId().equals(userId)) {
                return Result.error(403, "无权查看此计划");
            }
            List<PlanTask> tasks = planService.getAllTasks(planId);
            return Result.success(tasks);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 生成某阶段的详细任务（按需生成L2）
     */
    @PostMapping("/{planId}/phase/{phase}/generate")
    public Result<?> generatePhaseTasks(@RequestAttribute("userId") Long userId, @PathVariable Long planId, @PathVariable Integer phase) {
        try {
            Plan plan = planService.getPlanById(planId);
            if (plan == null || !plan.getUserId().equals(userId)) {
                return Result.error(403, "无权操作此计划");
            }
            List<PlanTask> tasks = planService.generatePhaseTasks(planId, phase);
            return Result.success("阶段任务生成成功", tasks);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 完成任务（统一走 doCheckin 路径，确保打卡记录、连续天数、成就都正确更新）
     */
    @PutMapping("/task/complete/{taskId}")
    public Result<?> completeTask(@RequestAttribute("userId") Long userId, @PathVariable Long taskId) {
        try {
            // 校验任务归属
            PlanTask task = planTaskMapper.selectById(taskId);
            if (task == null) {
                return Result.error("任务不存在");
            }
            Plan plan = planService.getPlanById(task.getPlanId());
            if (plan == null || !plan.getUserId().equals(userId)) {
                return Result.error(403, "无权操作此任务");
            }
            // 统一走 checkinService，确保创建打卡记录、更新 streak、触发成就
            checkinService.doCheckin(userId, null, taskId, null, null, null, null);
            return Result.success("任务完成");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 重新生成计划
     */
    @PostMapping("/regenerate/{planId}")
    public Result<?> regeneratePlan(@RequestAttribute("userId") Long userId, @PathVariable Long planId) {
        try {
            // 校验计划归属
            Plan plan = planService.getPlanById(planId);
            if (plan == null || !plan.getUserId().equals(userId)) {
                return Result.error(403, "无权操作此计划");
            }
            planService.adjustPlan(planId);
            return Result.success("计划已重新生成");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除计划
     */
    @DeleteMapping("/delete/{planId}")
    public Result<?> deletePlan(@RequestAttribute("userId") Long userId, @PathVariable Long planId) {
        try {
            // 校验计划归属
            Plan plan = planService.getPlanById(planId);
            if (plan == null || !plan.getUserId().equals(userId)) {
                return Result.error(403, "无权删除此计划");
            }
            planService.deletePlan(planId);
            return Result.success("删除成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取计划断卡详细信息
     */
    @GetMapping("/{planId}/streak-break-info")
    public Result<?> getStreakBreakInfo(@RequestAttribute("userId") Long userId, @PathVariable Long planId) {
        try {
            Plan plan = planService.getPlanById(planId);
            if (plan == null || !plan.getUserId().equals(userId)) {
                return Result.error(403, "无权查看此计划");
            }
            Map<String, Object> info = planService.getStreakBreakInfo(planId);
            return Result.success(info);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 计划反馈（用于优化缓存方案评分）
     */
    @PostMapping("/feedback")
    public Result<?> feedback(@RequestBody Map<String, Object> params) {
        try {
            if (params.get("templateId") == null || params.get("type") == null) {
                return Result.error("缺少必要参数");
            }
            Long templateId = Long.valueOf(params.get("templateId").toString());
            String type = (String) params.get("type"); // success, fail, score
            Integer score = params.get("score") != null ? Integer.valueOf(params.get("score").toString()) : null;

            if ("success".equals(type)) {
                planCacheService.incrementSuccess(templateId);
            } else if ("score".equals(type) && score != null) {
                planCacheService.updateScore(templateId, score.floatValue());
            }

            return Result.success("反馈成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 填充 Plan 的断卡统计瞬态字段
     */
    private void enrichPlan(Plan plan) {
        try {
            Map<String, Object> info = planService.getStreakBreakInfo(plan.getId());
            plan.setStreakBreakDays((Integer) info.get("streakBreakDays"));
            plan.setPendingDays((Integer) info.get("pendingDays"));
            plan.setFeasible((Boolean) info.get("feasible"));
            plan.setInfeasibleReason((String) info.get("infeasibleReason"));
        } catch (Exception e) {
            // 静默处理，不影响主流程
        }
    }
}