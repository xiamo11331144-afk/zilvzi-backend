package com.zilvzi.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zilvzi.mapper.GoalMapper;
import com.zilvzi.mapper.PartnerMapper;
import com.zilvzi.mapper.PlanMapper;
import com.zilvzi.mapper.PlanTaskMapper;
import com.zilvzi.mapper.UserMapper;
import com.zilvzi.pojo.Goal;
import com.zilvzi.pojo.Partner;
import com.zilvzi.pojo.Plan;
import com.zilvzi.pojo.PlanTask;
import com.zilvzi.pojo.PlanTemplate;
import com.zilvzi.pojo.User;
import com.zilvzi.service.AiService;
import com.zilvzi.service.NotificationService;
import com.zilvzi.service.PlanCacheService;
import com.zilvzi.service.PlanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 计划Service实现类（分层生成：L1大纲 + L2按需详细任务）
 */
@Service
public class PlanServiceImpl implements PlanService {

    @Autowired
    private PlanMapper planMapper;

    @Autowired
    private PlanTaskMapper planTaskMapper;

    @Autowired
    private GoalMapper goalMapper;

    @Autowired
    private AiService aiService;

    @Autowired
    private PlanCacheService planCacheService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private PartnerMapper partnerMapper;

    @Autowired
    private UserMapper userMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 第一步：生成计划大纲（L1路线图）
     * 快速返回给用户，展示阶段划分
     */
    @Override
    public Plan generatePlan(Long userId, Long goalId, Map<String, Object> params) {
        String goalType = (String) params.get("goalType");
        String goalName = (String) params.get("goalName");
        String examDate = (String) params.get("examDate");
        String startDateStr = (String) params.get("startDate");
        String remark = (String) params.get("remark");
        String scheduleData = (String) params.get("scheduleData");
        String scheduleDescription = (String) params.get("scheduleDescription");

        // 使用前端传来的totalDays，如果没有则自己计算
        Integer totalDays = params.get("totalDays") != null ? 
            Integer.valueOf(params.get("totalDays").toString()) : null;

        System.out.println("[Plan] 接收到参数: totalDays=" + totalDays + ", startDate=" + startDateStr + ", examDate=" + examDate);
        System.out.println("[Plan] scheduleDescription=" + scheduleDescription);

        String goalKeyword = extractKeyword(goalName);

        // 防重复：检查用户是否已有同名活跃计划
        System.out.println("[Plan] 防重复检查: userId=" + userId + " goalName=" + goalName);
        List<Plan> existingPlans = planMapper.selectByUserId(userId);
        System.out.println("[Plan] 用户现有计划数: " + existingPlans.size());
        for (Plan existing : existingPlans) {
            System.out.println("[Plan]   现有计划: id=" + existing.getId()
                    + " name=" + existing.getName()
                    + " status=" + existing.getStatus()
                    + " goalId=" + existing.getGoalId()
                    + " createTime=" + existing.getCreateTime());
        }
        for (Plan existing : existingPlans) {
            if ("active".equals(existing.getStatus()) && existing.getName() != null
                    && existing.getName().equals(goalName + "学习计划")) {
                System.out.println("[Plan] ✅ 命中去重: 已存在同名活跃计划 id=" + existing.getId()
                        + " name=" + existing.getName() + "，直接返回");
                return existing;
            }
        }
        System.out.println("[Plan] 未命中去重，将创建新计划");

        // 创建目标：先查是否已有同名活跃目标，避免重复创建
        if (goalId == null) {
            Goal existingGoal = goalMapper.selectActiveByName(userId, goalName);
            if (existingGoal != null) {
                goalId = existingGoal.getId();
                System.out.println("[Plan] ✅ 复用已有目标: goalId=" + goalId + " name=" + goalName);
            } else {
                Goal goal = new Goal();
                goal.setUserId(userId);
                goal.setName(goalName);
                goal.setType(goalType);
                goal.setDescription(remark);
                goal.setStartDate(startDateStr != null ? LocalDate.parse(startDateStr) : LocalDate.now());
                if (examDate != null) {
                    goal.setEndDate(LocalDate.parse(examDate));
                }
                goal.setStatus("active");
                goal.setStreak(0);
                goal.setTotalCheckins(0);
                goalMapper.insert(goal);
                goalId = goal.getId();
                System.out.println("[Plan] 新建目标: goalId=" + goalId + " name=" + goalName + " type=" + goalType);
            }
        } else {
            System.out.println("[Plan] 使用已有目标: goalId=" + goalId);
        }

        // 二次去重：基于 goalId 检查是否已有活跃计划（防止名称不匹配导致的漏检）
        if (goalId != null) {
            Plan existingByGoal = planMapper.selectByGoalId(goalId);
            if (existingByGoal != null && "active".equals(existingByGoal.getStatus())) {
                System.out.println("[Plan] ✅ 命中goalId去重: 已存在活跃计划 id=" + existingByGoal.getId()
                        + " goalId=" + goalId + "，直接返回");
                return existingByGoal;
            }
        }

        // 如果前端没有传totalDays，则根据日期计算
        if (totalDays == null) {
            if (startDateStr != null && examDate != null) {
                LocalDate start = LocalDate.parse(startDateStr);
                LocalDate end = LocalDate.parse(examDate);
                totalDays = (int) (end.toEpochDay() - start.toEpochDay()) + 1;
            } else if (examDate != null) {
                LocalDate end = LocalDate.parse(examDate);
                totalDays = (int) (end.toEpochDay() - LocalDate.now().toEpochDay()) + 1;
            } else {
                totalDays = 30;
            }
        }

        if (totalDays < 1) totalDays = 1;
        if (totalDays > 365) totalDays = 365;

        System.out.println("[Plan] 最终totalDays: " + totalDays);

        // 创建计划
        Plan plan = new Plan();
        plan.setUserId(userId);
        plan.setGoalId(goalId);
        plan.setName(goalName + "学习计划");
        plan.setTotalDays(totalDays);
        plan.setCompletedDays(0);
        plan.setScheduleData(scheduleDescription);
        plan.setRemark(remark);
        plan.setCurrentPhase(1);
        plan.setStatus("active");

        // 如果有时间安排信息，不使用缓存，直接调用AI
        String outline;
        boolean hasSchedule = scheduleDescription != null && !scheduleDescription.isEmpty() && scheduleDescription.length() > 20;
        
        if (hasSchedule) {
            // 有时间安排，直接调用AI生成
            System.out.println("[AI] 有时间安排信息，调用AI生成大纲");
            try {
                outline = aiService.generateOutline(goalType, goalName, examDate, remark, totalDays, scheduleDescription);
            } catch (Exception e) {
                e.printStackTrace();
                outline = generateDefaultOutline(totalDays);
            }
        } else {
            // 没有时间安排，尝试从缓存获取
            PlanTemplate cachedTemplate = planCacheService.matchTemplate(goalType, goalKeyword, totalDays);
            if (cachedTemplate != null && cachedTemplate.getPlanOutline() != null && !cachedTemplate.getPlanOutline().isEmpty()) {
                System.out.println("[Cache] 使用缓存大纲");
                outline = cachedTemplate.getPlanOutline();
                planCacheService.incrementUsage(cachedTemplate.getId());
            } else {
                System.out.println("[AI] 无缓存，调用AI生成大纲");
                try {
                    outline = aiService.generateOutline(goalType, goalName, examDate, remark, totalDays, scheduleDescription);
                    saveOutlineToCache(goalType, goalKeyword, totalDays, outline);
                } catch (Exception e) {
                    e.printStackTrace();
                    outline = generateDefaultOutline(totalDays);
                }
            }
        }

        // 验证并修正AI返回的大纲，确保天数正确
        outline = validateOutline(outline, totalDays);

        plan.setOutline(outline);
        planMapper.insert(plan);

        System.out.println("[Plan] ✅ 新计划创建成功: planId=" + plan.getId()
                + " name=" + plan.getName()
                + " goalId=" + plan.getGoalId()
                + " totalDays=" + plan.getTotalDays()
                + " userId=" + plan.getUserId()
                + " status=" + plan.getStatus());

        return plan;
    }

    /**
     * 第二步：按需生成某阶段的详细任务（L2）
     * 用户进入某阶段时调用
     */
    @Override
    @Transactional
    public List<PlanTask> generatePhaseTasks(Long planId, Integer phase) {
        Plan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new RuntimeException("计划不存在");
        }

        // 自动标记断卡任务（确保返回的任务包含正确的断卡状态）
        autoMarkStreakBreak(planId);

        // 解析大纲，获取当前阶段信息
        PhaseInfo phaseInfo = parsePhaseInfo(plan.getOutline(), phase);
        if (phaseInfo == null) {
            throw new RuntimeException("阶段信息不存在");
        }

        System.out.println("[Phase] 阶段" + phase + "：第" + phaseInfo.startDay + "-" + phaseInfo.endDay + "天，主题：" + phaseInfo.theme);

        // 检查该阶段任务是否已生成（每次都查DB，确保包含补发任务）
        List<PlanTask> existingTasks = planTaskMapper.selectByPlanIdAndPhase(planId, phaseInfo.startDay, phaseInfo.endDay);
        if (existingTasks != null && !existingTasks.isEmpty()) {
            System.out.println("[Cache] 阶段" + phase + "任务已存在（含补发），返回" + existingTasks.size() + "个");
            return existingTasks;
        }

        // 一次性生成整个阶段的任务
        List<PlanTask> allTasks = new ArrayList<>();
        int totalDays = phaseInfo.endDay - phaseInfo.startDay + 1;
        
        // 获取时间安排描述
        String scheduleDescription = plan.getScheduleData() != null ? plan.getScheduleData() : "";
        
        System.out.println("[AI] 生成阶段" + phase + "全部任务：第" + phaseInfo.startDay + "-" + phaseInfo.endDay + "天，共" + totalDays + "天");
        System.out.println("[AI] 时间安排: " + scheduleDescription);
        
        int maxRetries = 3;
        boolean success = false;
        
        for (int retry = 1; retry <= maxRetries; retry++) {
            try {
                String aiResult = aiService.generatePhaseTasks(
                        "exam",
                        plan.getName().replace("学习计划", ""),
                        plan.getRemark(),
                        phase,
                        totalDays,
                        phaseInfo.theme,
                        scheduleDescription
                );
                
                System.out.println("[AI] 返回结果长度: " + (aiResult != null ? aiResult.length() : 0));
                
                List<PlanTask> tasks = parsePhaseTasks(planId, aiResult, phaseInfo.startDay, phaseInfo.endDay);
                System.out.println("[Parse] 解析到" + tasks.size() + "个任务");
                
                if (!tasks.isEmpty()) {
                    planTaskMapper.batchInsert(tasks);
                    allTasks.addAll(tasks);
                    success = true;
                    break;
                }
            } catch (Exception e) {
                System.out.println("[Error] 第" + retry + "次尝试失败: " + e.getMessage());
                if (retry < maxRetries) {
                    System.out.println("[Retry] 等待3秒后重试...");
                    try { Thread.sleep(3000); } catch (InterruptedException ie) { }
                }
            }
        }
        
        // 如果失败，使用默认任务
        if (!success) {
            System.out.println("[Default] 使用默认任务");
            allTasks = generateDefaultPhaseTasks(planId, phaseInfo.startDay, phaseInfo.endDay);
            planTaskMapper.batchInsert(allTasks);
        }

        // 更新当前阶段
        plan.setCurrentPhase(phase);
        planMapper.update(plan);

        return allTasks;
    }

    /**
     * 解析阶段信息
     */
    private PhaseInfo parsePhaseInfo(String outline, Integer phase) {
        try {
            String json = extractJson(outline);
            JsonNode root = objectMapper.readTree(json);
            JsonNode phases = root.get("phases");

            if (phases != null) {
                for (JsonNode phaseNode : phases) {
                    if (phaseNode.get("phase").asInt() == phase) {
                        PhaseInfo info = new PhaseInfo();
                        info.phase = phase;
                        info.name = phaseNode.get("name").asText();
                        info.startDay = phaseNode.get("startDay").asInt();
                        info.endDay = phaseNode.get("endDay").asInt();
                        info.theme = phaseNode.get("theme").asText();
                        return info;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 解析阶段任务
     */
    private List<PlanTask> parsePhaseTasks(Long planId, String aiResult, int startDay, int endDay) {
        Plan plan = planMapper.selectById(planId);
        LocalDate planStartDate = plan != null && plan.getCreateTime() != null
                ? plan.getCreateTime().toLocalDate() : LocalDate.now();

        List<PlanTask> tasks = new ArrayList<>();
        try {
            String json = extractJson(aiResult);
            JsonNode root = objectMapper.readTree(json);
            JsonNode dailyPlan = root.get("dailyPlan");

            if (dailyPlan != null) {
                // H8: 基于计划起始日期计算任务日期，而非 LocalDate.now()
                LocalDate startDate = planStartDate.plusDays(startDay - 1);
                int dayIndex = 0;

                for (JsonNode dayNode : dailyPlan) {
                    int day = startDay + dayIndex;
                    if (day > endDay) break;

                    LocalDate taskDate = startDate.plusDays(dayIndex);
                    JsonNode tasksNode = dayNode.get("tasks");
                    int order = 1;

                    for (JsonNode taskNode : tasksNode) {
                        PlanTask task = new PlanTask();
                        task.setPlanId(planId);
                        task.setDayNumber(day);
                        task.setTaskDate(taskDate);
                        task.setTaskName(taskNode.get("name").asText());
                        task.setTaskType(taskNode.has("type") ? taskNode.get("type").asText() : "学习");
                        task.setTaskDescription(taskNode.get("description").asText());
                        task.setTaskDuration(taskNode.get("duration").asText());
                        task.setTaskIcon(taskNode.has("icon") ? taskNode.get("icon").asText() : "📚");
                        task.setTaskColor(taskNode.has("color") ? taskNode.get("color").asText() : "#4A90D9");
                        task.setSortOrder(order++);
                        task.setCompleted(0);
                        tasks.add(task);
                    }
                    dayIndex++;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return tasks;
    }

    private String extractJson(String text) {
        if (text.contains("```json")) {
            return text.substring(text.indexOf("```json") + 7, text.lastIndexOf("```")).trim();
        } else if (text.contains("```")) {
            return text.substring(text.indexOf("```") + 3, text.lastIndexOf("```")).trim();
        }
        return text.trim();
    }

    private String extractKeyword(String goalName) {
        if (goalName == null || goalName.isEmpty()) return "通用";
        String[] keywords = {"考研", "四六级", "英语", "考公", "健身", "减脂", "阅读", "编程", "软件设计师", "会计师"};
        for (String keyword : keywords) {
            if (goalName.contains(keyword)) {
                return keyword;
            }
        }
        return goalName.length() > 4 ? goalName.substring(0, 4) : goalName;
    }

    /**
     * 验证并修正大纲，确保天数正确
     */
    private String validateOutline(String outline, int totalDays) {
        try {
            String json = extractJson(outline);
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(json);
            com.fasterxml.jackson.databind.JsonNode phases = root.get("phases");

            if (phases != null && phases.size() > 0) {
                // 获取最后一个阶段
                com.fasterxml.jackson.databind.JsonNode lastPhase = phases.get(phases.size() - 1);
                int lastEndDay = lastPhase.get("endDay").asInt();

                // 如果最后一个阶段的endDay不等于totalDays，需要修正
                if (lastEndDay != totalDays) {
                    System.out.println("[Validate] 修正大纲天数: " + lastEndDay + " -> " + totalDays);

                    // 重新计算每个阶段的天数
                    int phaseCount = phases.size();
                    int daysPerPhase = totalDays / phaseCount;
                    int extraDays = totalDays % phaseCount;

                    com.fasterxml.jackson.databind.node.ArrayNode newPhases = objectMapper.createArrayNode();
                    int currentDay = 1;

                    for (int i = 0; i < phaseCount; i++) {
                        int phaseDays = daysPerPhase + (i < extraDays ? 1 : 0);
                        com.fasterxml.jackson.databind.node.ObjectNode newPhase = objectMapper.createObjectNode();
                        newPhase.put("phase", i + 1);
                        newPhase.put("name", phases.get(i).get("name").asText());
                        newPhase.put("startDay", currentDay);
                        newPhase.put("endDay", currentDay + phaseDays - 1);
                        newPhase.put("theme", phases.get(i).get("theme").asText());
                        newPhase.set("goals", phases.get(i).get("goals"));
                        newPhase.set("topics", phases.get(i).get("topics"));
                        newPhases.add(newPhase);
                        currentDay += phaseDays;
                    }

                    ((com.fasterxml.jackson.databind.node.ObjectNode) root).set("phases", newPhases);
                    return objectMapper.writeValueAsString(root);
                }
            }
        } catch (Exception e) {
            System.out.println("[Validate] 验证大纲失败: " + e.getMessage());
        }
        return outline;
    }

    private void saveOutlineToCache(String goalType, String goalKeyword, int totalDays, String outline) {
        try {
            PlanTemplate template = new PlanTemplate();
            template.setGoalType(goalType);
            template.setGoalKeyword(goalKeyword);
            template.setTotalDays(totalDays);
            template.setDifficulty("medium");
            template.setPlanOutline(outline);
            template.setDailyTasksTemplate("{}");
            template.setAiModel("deepseek-chat");
            template.setUsageCount(0);
            template.setSuccessCount(0);
            template.setQualityScore(5.0f);
            planCacheService.saveTemplate(template);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String generateDefaultOutline(int totalDays) {
        int phaseDays = totalDays / 3;
        return String.format("""
                {
                    "phases": [
                        {"phase": 1, "name": "基础阶段", "days": "1-%d", "startDay": 1, "endDay": %d, "theme": "掌握基础知识", "goals": ["打好基础"], "topics": ["基础知识"]},
                        {"phase": 2, "name": "进阶阶段", "days": "%d-%d", "startDay": %d, "endDay": %d, "theme": "深入学习", "goals": ["提升能力"], "topics": ["进阶内容"]},
                        {"phase": 3, "name": "冲刺阶段", "days": "%d-%d", "startDay": %d, "endDay": %d, "theme": "强化冲刺", "goals": ["查漏补缺"], "topics": ["重点突破"]}
                    ],
                    "tips": ["坚持学习", "及时复习"]
                }
                """, phaseDays, phaseDays, phaseDays + 1, phaseDays * 2, phaseDays + 1, phaseDays * 2,
                phaseDays * 2 + 1, totalDays, phaseDays * 2 + 1, totalDays);
    }

    private List<PlanTask> generateDefaultPhaseTasks(Long planId, int startDay, int endDay) {
        List<PlanTask> tasks = new ArrayList<>();
        Plan plan = planMapper.selectById(planId);
        LocalDate planStartDate = plan != null && plan.getCreateTime() != null
                ? plan.getCreateTime().toLocalDate() : LocalDate.now();
        LocalDate startDate = planStartDate.plusDays(startDay - 1);

        for (int day = startDay; day <= endDay; day++) {
            LocalDate taskDate = startDate.plusDays(day - startDay);

            PlanTask task1 = new PlanTask();
            task1.setPlanId(planId);
            task1.setDayNumber(day);
            task1.setTaskDate(taskDate);
            task1.setTaskName("学习任务");
            task1.setTaskType("学习");
            task1.setTaskDescription("完成今日学习目标");
            task1.setTaskDuration("60分钟");
            task1.setTaskIcon("📚");
            task1.setTaskColor("#4A90D9");
            task1.setSortOrder(1);
            task1.setCompleted(0);
            tasks.add(task1);

            PlanTask task2 = new PlanTask();
            task2.setPlanId(planId);
            task2.setDayNumber(day);
            task2.setTaskDate(taskDate);
            task2.setTaskName("复习总结");
            task2.setTaskType("复习");
            task2.setTaskDescription("复习今日所学内容");
            task2.setTaskDuration("30分钟");
            task2.setTaskIcon("📝");
            task2.setTaskColor("#52C41A");
            task2.setSortOrder(2);
            task2.setCompleted(0);
            tasks.add(task2);
        }

        return tasks;
    }

    @Override
    public List<Plan> getUserPlans(Long userId) {
        return planMapper.selectByUserId(userId);
    }

    @Override
    public Plan getPlanById(Long id) {
        return planMapper.selectById(id);
    }

    @Override
    public List<PlanTask> getDayTasks(Long planId, Integer dayNumber) {
        autoMarkStreakBreak(planId);
        return planTaskMapper.selectByPlanIdAndDay(planId, dayNumber);
    }

    @Override
    public List<PlanTask> getAllTasks(Long planId) {
        autoMarkStreakBreak(planId);
        return planTaskMapper.selectByPlanId(planId);
    }

    @Override
    public void completeTask(Long taskId) {
        planTaskMapper.updateCompleted(taskId, 1);
        // 更新计划的已完成天数
        PlanTask task = planTaskMapper.selectById(taskId);
        if (task != null) {
            updatePlanCompletedDays(task.getPlanId());
        }
    }

    /**
     * 断卡后重新分配未完成任务到后续天数
     * 包含可行性检查：断卡过多或剩余时间不足时跳过补发，提示用户
     */
    @Override
    public void adjustPlan(Long planId) {
        Plan plan = planMapper.selectById(planId);
        if (plan == null) return;

        LocalDate planStartDate = plan.getCreateTime().toLocalDate();
        LocalDate today = LocalDate.now();
        int todayDayNumber = (int) (today.toEpochDay() - planStartDate.toEpochDay()) + 1;
        int totalPlanDays = plan.getTotalDays();

        // 1. 只获取原始任务中的过期未完成任务（排除补发任务，防止二次补发）
        // completed != 1 包含 0（未标记）和 -1（已被 autoMarkStreakBreak 标记）
        List<PlanTask> missedTasks = planTaskMapper.selectIncompletePastDaysExcludeSupplement(planId, LocalDate.now());
        if (missedTasks.isEmpty()) {
            System.out.println("[Adjust] 计划" + planId + "没有断卡任务，无需调整");
            return;
        }

        // 1.5 过滤已有补发任务的天数，防止重复补发
        List<PlanTask> existingSupplementsForFilter = planTaskMapper.selectSupplementTasks(planId);
        java.util.Set<Integer> supplementedDays = new java.util.HashSet<>();
        if (existingSupplementsForFilter != null) {
            for (PlanTask st : existingSupplementsForFilter) {
                // 从 description 中提取原始天数: "(补第X天)"
                String desc = st.getTaskDescription();
                if (desc != null && desc.contains("(补第") && desc.contains("天)")) {
                    try {
                        int start = desc.indexOf("(补第") + 3;
                        int end = desc.indexOf("天)", start);
                        supplementedDays.add(Integer.parseInt(desc.substring(start, end)));
                    } catch (Exception ignored) {}
                }
            }
        }
        int beforeFilter = missedTasks.size();
        missedTasks.removeIf(t -> supplementedDays.contains(t.getDayNumber()));
        if (missedTasks.isEmpty()) {
            System.out.println("[Adjust] 计划" + planId + "断卡任务已全部补发过，跳过");
            return;
        }
        System.out.println("[Adjust] 计划" + planId + "过滤已补发后剩余: " + missedTasks.size() + "/" + beforeFilter + "个断卡任务");

        int missedCount = missedTasks.size();
        int remainingDays = totalPlanDays - todayDayNumber; // 明天到计划结束的天数

        System.out.println("[Adjust] 计划" + planId + "断卡任务数: " + missedCount + ", 今天是第" + todayDayNumber + "天, 剩余可用天数: " + remainingDays);
        for (PlanTask t : missedTasks) {
            System.out.println("[Adjust]   断卡任务: id=" + t.getId() + " day=" + t.getDayNumber() + " name=" + t.getTaskName() + " completed=" + t.getCompleted());
        }

        // 2. 将所有断卡任务标记为过期（无论是否可行都要标记）
        for (PlanTask task : missedTasks) {
            planTaskMapper.updateStreakBreakStatus(task.getId(), -1, "⚠️", "#FF4D4F");
        }

        // 3. 计划已结束 → 仅标记，不补发
        if (remainingDays <= 0) {
            System.out.println("[Adjust] 计划已结束，仅标记断卡");
            updatePlanCompletedDays(planId);
            notifyStreakBreak(plan, false, missedCount, 0, false);
            return;
        }

        // 4. 可行性检查：满足任一条件则判定不可行
        boolean feasible = true;
        String infeasibleReason = "";

        // 条件A：断卡天数超过剩余天数，塞不下
        if (missedCount > remainingDays) {
            feasible = false;
            infeasibleReason = "断卡天数(" + missedCount + ")超过剩余天数(" + remainingDays + ")";
        }
        // 条件B：断卡任务与剩余天数之比超过 50%，每天要完成双倍任务
        else if ((double) missedCount / remainingDays > 0.5) {
            feasible = false;
            infeasibleReason = "断卡任务占比过高(" + missedCount + "/" + remainingDays + ")";
        }
        // 条件C：断卡天数超过计划总天数的 1/3
        else if ((double) missedCount / totalPlanDays > 1.0 / 3) {
            feasible = false;
            infeasibleReason = "断卡天数超过计划总天数的1/3(" + missedCount + "/" + totalPlanDays + ")";
        }

        if (!feasible) {
            System.out.println("[Adjust] 计划不可行: " + infeasibleReason + "，跳过补发，用户可自行按原计划打卡");
            updatePlanCompletedDays(planId);
            notifyStreakBreak(plan, false, missedCount, remainingDays, true);
            return;
        }

        // 5. 可行 → 执行补发
        List<PlanTask> existingSupplements = planTaskMapper.selectSupplementTasks(planId);
        int supplementCount = existingSupplements != null ? existingSupplements.size() : 0;
        int maxSupplement = remainingDays * 2;
        int allowedNew = Math.min(missedCount, maxSupplement - supplementCount);

        if (allowedNew <= 0) {
            System.out.println("[Adjust] 补发任务已达到上限，跳过补发");
            updatePlanCompletedDays(planId);
            notifyStreakBreak(plan, false, missedCount, remainingDays, false);
            return;
        }

        // 从今天开始补发，均匀分散到后续天数
        int tasksPerDay = (int) Math.ceil((double) allowedNew / remainingDays);
        if (tasksPerDay < 1) tasksPerDay = 1;

        int actualSupplemented = 0;
        for (int i = 0; i < allowedNew; i++) {
            int dayOffset = i / tasksPerDay; // 今天=0，明天=1，后天=2，...
            int targetDayNumber = todayDayNumber + dayOffset;
            LocalDate targetDate = today.plusDays(dayOffset);

            if (targetDayNumber > totalPlanDays) break;

            PlanTask missed = missedTasks.get(i);

            PlanTask extraTask = new PlanTask();
            extraTask.setPlanId(planId);
            extraTask.setDayNumber(targetDayNumber);
            extraTask.setTaskDate(targetDate);
            extraTask.setTaskName("【补】" + missed.getTaskName());
            extraTask.setTaskType(missed.getTaskType());
            extraTask.setTaskDescription(missed.getTaskDescription() + "(补第" + missed.getDayNumber() + "天)");
            extraTask.setTaskDuration(missed.getTaskDuration());
            extraTask.setTaskIcon("🔄");
            extraTask.setTaskColor("#FAAD14");
            extraTask.setSortOrder(99);
            extraTask.setCompleted(0);

            planTaskMapper.singleInsert(extraTask);
            actualSupplemented++;
        }

        // 6. 更新计划的已完成天数
        updatePlanCompletedDays(planId);

        // 7. 通知用户和搭子
        notifyStreakBreak(plan, true, missedCount, remainingDays, false);

        System.out.println("[Adjust] 已将" + actualSupplemented + "个断卡任务补到后续" + remainingDays + "天");
    }

    /**
     * 发送断卡通知
     */
    private void notifyStreakBreak(Plan plan, boolean supplemented, int missedCount, int remainingDays, boolean infeasible) {
        User user = userMapper.selectById(plan.getUserId());
        String userName = user != null ? user.getNickname() : "用户";

        String title;
        String content;

        if (infeasible) {
            title = "计划调整提醒";
            content = "你已断卡" + missedCount + "天，剩余" + remainingDays + "天不足以补回，"
                    + "补卡机制已暂停。你可以继续按原计划打卡，无需补回之前的任务。";
        } else if (supplemented) {
            title = "断卡提醒";
            content = "你有" + missedCount + "天未完成打卡，已自动将任务补到后续" + remainingDays + "天中。";
        } else {
            title = "断卡提醒";
            content = "你有" + missedCount + "天未完成打卡任务。";
        }

        // 通知用户本人
        notificationService.sendNotification(plan.getUserId(), null, "streak_break",
                title, content, "plan", plan.getId());

        // 通知搭子（selectByUserId 已包含双向查询，无需二次循环）
        String partnerMsg = infeasible
                ? userName + "的计划已断卡" + missedCount + "天，补卡机制已暂停"
                : userName + "有" + missedCount + "天未完成打卡";

        List<Partner> partners = partnerMapper.selectByUserId(plan.getUserId());
        for (Partner p : partners) {
            Long notifyTarget = p.getUserId().equals(plan.getUserId()) ? p.getPartnerId() : p.getUserId();
            notificationService.sendNotification(notifyTarget, plan.getUserId(), "streak_break",
                    "搭子断卡", partnerMsg, "plan", plan.getId());
        }
    }

    @Override
    public void deletePlan(Long planId) {
        planTaskMapper.deleteByPlanId(planId);
        planMapper.deleteById(planId);
    }

    @Override
    public void updatePlan(Plan plan) {
        planMapper.update(plan);
    }

    /**
     * 更新计划的已完成天数
     */
    private void updatePlanCompletedDays(Long planId) {
        try {
            // 查询该计划已完成的任务天数（distinct day_number）
            Integer completedDays = planTaskMapper.countCompletedDays(planId);
            if (completedDays == null) completedDays = 0;

            // 更新plan表
            planMapper.updateCompletedDays(planId, completedDays);

            System.out.println("[Plan] 更新计划" + planId + "已完成天数: " + completedDays);
        } catch (Exception e) {
            System.out.println("[Plan] 更新计划已完成天数失败: " + e.getMessage());
        }
    }

    /**
     * 自动将过期未完成的任务标记为断卡（completed = -1）
     * 仅标记，不触发补发逻辑，避免查询时产生副作用
     * 同时将断卡任务的图标改为警告标记、颜色改为红色
     */
    private void autoMarkStreakBreak(Long planId) {
        try {
            List<PlanTask> incompletePastTasks = planTaskMapper.selectIncompletePastDays(planId, LocalDate.now());
            if (incompletePastTasks != null && !incompletePastTasks.isEmpty()) {
                for (PlanTask task : incompletePastTasks) {
                    System.out.println("[AutoMark] 计划" + planId + " 标记断卡: id=" + task.getId() + " day=" + task.getDayNumber() + " name=" + task.getTaskName() + " completed=" + task.getCompleted() + " → -1");
                    planTaskMapper.updateStreakBreakStatus(task.getId(), -1, "⚠️", "#FF4D4F");
                }
                System.out.println("[AutoMark] 计划" + planId + " 自动标记" + incompletePastTasks.size() + "个断卡任务（已排除补发任务）");
            }
        } catch (Exception e) {
            System.out.println("[AutoMark] 自动标记断卡失败: " + e.getMessage());
        }
    }

    /**
     * 阶段信息内部类
     */
    private static class PhaseInfo {
        int phase;
        String name;
        int startDay;
        int endDay;
        String theme;
    }

    /**
     * 获取计划的断卡统计信息
     * 包含：断卡天数、断卡任务列表、过期未打卡天数
     */
    @Override
    public Map<String, Object> getStreakBreakInfo(Long planId) {
        Map<String, Object> info = new java.util.HashMap<>();

        Integer streakBreakDays = planTaskMapper.countStreakBreakDays(planId);
        int sbDays = streakBreakDays != null ? streakBreakDays : 0;
        info.put("streakBreakDays", sbDays);

        List<PlanTask> streakBreakTasks = planTaskMapper.selectStreakBreakTasks(planId);
        info.put("streakBreakTasks", streakBreakTasks != null ? streakBreakTasks : new ArrayList<>());

        Integer pendingDays = planTaskMapper.countPendingDays(planId, LocalDate.now());
        info.put("pendingDays", pendingDays != null ? pendingDays : 0);

        // 计算计划可行性
        Plan plan = planMapper.selectById(planId);
        if (plan != null && plan.getCreateTime() != null) {
            LocalDate planStartDate = plan.getCreateTime().toLocalDate();
            LocalDate today = LocalDate.now();
            int todayDayNumber = (int) (today.toEpochDay() - planStartDate.toEpochDay()) + 1;
            int totalPlanDays = plan.getTotalDays();
            int remainingDays = totalPlanDays - todayDayNumber;

            // 统计原始断卡任务数（排除补发任务）
            int originalMissedCount = 0;
            if (streakBreakTasks != null) {
                for (PlanTask t : streakBreakTasks) {
                    if (t.getTaskName() != null && !t.getTaskName().startsWith("【补】")) {
                        originalMissedCount++;
                    }
                }
            }

            boolean feasible = true;
            String infeasibleReason = "";

            if (remainingDays <= 0) {
                feasible = false;
                infeasibleReason = "计划已结束";
            } else if (originalMissedCount > remainingDays) {
                feasible = false;
                infeasibleReason = "断卡天数超过剩余天数";
            } else if ((double) originalMissedCount / remainingDays > 0.5) {
                feasible = false;
                infeasibleReason = "断卡任务占比过高";
            } else if ((double) originalMissedCount / totalPlanDays > 1.0 / 3) {
                feasible = false;
                infeasibleReason = "断卡天数超过计划总天数的1/3";
            }

            info.put("feasible", feasible);
            info.put("infeasibleReason", infeasibleReason);
            info.put("remainingDays", Math.max(remainingDays, 0));
            info.put("originalMissedCount", originalMissedCount);
        } else {
            info.put("feasible", true);
            info.put("infeasibleReason", "");
            info.put("remainingDays", 0);
            info.put("originalMissedCount", 0);
        }

        return info;
    }
}