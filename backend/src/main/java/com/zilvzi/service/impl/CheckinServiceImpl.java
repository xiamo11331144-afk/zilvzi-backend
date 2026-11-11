package com.zilvzi.service.impl;

import com.zilvzi.mapper.CheckinMapper;
import com.zilvzi.mapper.GoalMapper;
import com.zilvzi.mapper.MessageMapper;
import com.zilvzi.mapper.PartnerMapper;
import com.zilvzi.mapper.PlanMapper;
import com.zilvzi.mapper.PlanTaskMapper;
import com.zilvzi.pojo.Checkin;
import com.zilvzi.pojo.Goal;
import com.zilvzi.pojo.Message;
import com.zilvzi.pojo.Partner;
import com.zilvzi.pojo.Plan;
import com.zilvzi.pojo.PlanTask;
import com.zilvzi.service.AchievementService;
import com.zilvzi.service.CheckinService;
import com.zilvzi.service.NotificationService;
import com.zilvzi.service.PlanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 打卡Service实现类
 */
@Service
public class CheckinServiceImpl implements CheckinService {

    @Autowired
    private CheckinMapper checkinMapper;

    @Autowired
    private GoalMapper goalMapper;

    @Autowired
    private PlanTaskMapper planTaskMapper;

    @Autowired
    private PlanMapper planMapper;

    @Autowired
    private AchievementService achievementService;

    @Autowired
    private PlanService planService;

    @Autowired
    private PartnerMapper partnerMapper;

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private NotificationService notificationService;

    /** 将目标类型标准化为统一中文（与 plan_task.task_type 对齐） */
    private String normalizeGoalType(String type) {
        if (type == null) return null;
        return switch (type.toLowerCase()) {
            case "exam", "考试", "考试备考" -> "考试";
            case "study", "学习", "学习提升" -> "学习";
            case "fitness", "运动", "健身", "健身运动", "锻炼", "体育" -> "运动";
            case "reading", "阅读", "阅读计划" -> "阅读";
            case "skill", "技能", "技能学习" -> "技能";
            case "habit", "习惯", "习惯养成" -> "习惯";
            default -> type;
        };
    }

    /** 判断两个类型是否属于同一目标类别 */
    private boolean isSameGoalCategory(String type1, String type2) {
        if (type1 == null || type2 == null) return false;
        String n1 = normalizeGoalType(type1);
        String n2 = normalizeGoalType(type2);
        return n1.equals(n2);
    }

    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Checkin doCheckin(Long userId, Long goalId, Long taskId, String content, String image, String proofImage, String proofContent) {
        String today = LocalDate.now().toString();

        // 如果是plan_task打卡
        if (taskId != null) {
            PlanTask task = planTaskMapper.selectById(taskId);
            if (task == null) {
                throw new RuntimeException("任务不存在");
            }

            // C2: 校验任务归属 — 确保 task 对应的 plan 属于当前用户
            Plan plan = planMapper.selectById(task.getPlanId());
            if (plan == null || !plan.getUserId().equals(userId)) {
                throw new RuntimeException("无权操作此任务");
            }

            requireActiveOwnedGoal(userId, plan.getGoalId());
            goalMapper.lockById(plan.getGoalId());

            System.out.println("[Checkin] userId=" + userId + " taskId=" + taskId + " day=" + task.getDayNumber() + " name=" + task.getTaskName() + " completed=" + task.getCompleted() + " taskDate=" + task.getTaskDate());

            Long actualGoalId = plan.getGoalId();
            String checkinDate = task.getTaskDate() != null ? task.getTaskDate().toString() : today;
            Checkin existingCheckin = checkinMapper.selectByTaskAndDate(taskId, checkinDate);
            if (existingCheckin != null && !"rejected".equals(existingCheckin.getStatus())) {
                return existingCheckin;
            }
            if (task.getCompleted() == 1 && existingCheckin == null) {
                throw new RuntimeException("今日已打卡");
            }
            // 驳回的记录可补充凭证；其余任务保留原有日期限制。
            if (existingCheckin == null && task.getCompleted() != -1 && task.getTaskDate() != null
                    && !task.getTaskDate().equals(LocalDate.now())) {
                throw new RuntimeException("该任务不在可打卡日期");
            }
            {
                // 获取当前任务的目标类型
                String taskGoalType = task.getTaskType();
                if (taskGoalType == null && actualGoalId != null) {
                    Goal goal = goalMapper.selectById(actualGoalId);
                    if (goal != null) taskGoalType = goal.getType();
                }
                final String goalType = taskGoalType;

                // 按目标类型匹配搭子
                List<Partner> partners = partnerMapper.selectByUserId(userId);
                System.out.println("[CheckinPartner] userId=" + userId + " taskType=" + taskGoalType
                        + " partners=" + partners.size());
                boolean hasPartner = goalType != null && !goalType.isEmpty() && partners.stream()
                        .filter(p -> "accepted".equals(p.getStatus()))
                        .anyMatch(p -> {
                            Long partnerUserId = p.getUserId().equals(userId) ? p.getPartnerId() : p.getUserId();
                            List<Goal> partnerGoals = goalMapper.selectByUserId(partnerUserId);
                            boolean match = partnerGoals.stream().anyMatch(g -> isSameGoalCategory(goalType, g.getType()));
                            System.out.println("[CheckinPartner]   partner=" + partnerUserId
                                    + " goals=" + partnerGoals.stream().map(g -> g.getType()).toList()
                                    + " match=" + match);
                            return match;
                        });
                System.out.println("[CheckinPartner] hasPartner=" + hasPartner
                        + " proofImage=" + (proofImage != null ? "YES" : "null"));

                Checkin checkin = new Checkin();
                checkin.setUserId(userId);
                checkin.setGoalId(actualGoalId);
                checkin.setTaskId(taskId);
                checkin.setContent(content);
                checkin.setImage(image);
                checkin.setCheckinTime(LocalDateTime.now());
                checkin.setDate(checkinDate);

                if (hasPartner) {
                    // 有搭子：提交凭证，等待审核
                    checkin.setStatus("pending");
                    checkin.setProofImage(proofImage);
                    checkin.setProofContent(proofContent);
                    saveSubmission(checkin, existingCheckin);
                    if (task.getCompleted() == 1) {
                        planTaskMapper.updateCompleted(taskId, 0);
                        updatePlanCompletedDays(task.getPlanId());
                    }
                    // 通知同类型目标的所有搭子审核 + 发送打卡凭证私信
                    for (Partner p : partners) {
                        if ("accepted".equals(p.getStatus())) {
                            Long partnerUserId = p.getUserId().equals(userId) ? p.getPartnerId() : p.getUserId();
                            List<Goal> pGoals = goalMapper.selectByUserId(partnerUserId);
                            boolean typeMatch = pGoals.stream().anyMatch(g -> isSameGoalCategory(goalType, g.getType()));
                            if (typeMatch) {
                                notificationService.sendNotification(partnerUserId, userId, "checkin_review",
                                        "打卡待审核", "你的搭子提交了新的打卡凭证，快去审核吧！", "checkin", checkin.getId());
                                // 发送打卡凭证私信（含图片URL供前端展示）
                                String msgContent = "📸 打卡凭证" + (task.getTaskName() != null ? "：" + task.getTaskName() : "");
                                if (proofContent != null && !proofContent.isEmpty()) {
                                    msgContent += "\n📝 " + proofContent;
                                }
                                if (proofImage != null && !proofImage.isEmpty()) {
                                    msgContent += "\n[IMG]" + proofImage + "[/IMG]";
                                }
                                Message msg = new Message();
                                msg.setFromUserId(userId);
                                msg.setToUserId(partnerUserId);
                                msg.setContent(msgContent);
                                msg.setType("checkin_proof");
                                msg.setRelatedId(checkin.getId());
                                messageMapper.insert(msg);
                            }
                        }
                    }
                } else {
                    // 无搭子：直接通过，兼容自打卡
                    checkin.setStatus("approved");
                    saveSubmission(checkin, existingCheckin);
                    completeTask(task);
                    updateGoalStatsAndAchievement(userId, actualGoalId);
                }
            }

            // H6: 使用正确的 checkinDate 查询返回值
            return checkinMapper.selectByTaskAndDate(taskId, checkinDate);
        }

        // 原来的goal打卡逻辑
        if (goalId != null) {
            Goal goalObj = requireActiveOwnedGoal(userId, goalId);
            goalMapper.lockById(goalId);
            Checkin existing = checkinMapper.selectByUserAndDate(userId, goalId, today);
            if (existing != null && !"rejected".equals(existing.getStatus())) {
                return existing;
            }

            Checkin checkin = new Checkin();
            checkin.setUserId(userId);
            checkin.setGoalId(goalId);
            checkin.setContent(content);
            checkin.setImage(image);
            checkin.setCheckinTime(LocalDateTime.now());
            checkin.setDate(today);

            // 获取目标类型，按类型匹配搭子
            final String goalType = goalObj.getType();

            // 按目标类型匹配搭子
            List<Partner> partners = partnerMapper.selectByUserId(userId);
            boolean hasPartner = goalType != null && !goalType.isEmpty() && partners.stream()
                    .filter(p -> "accepted".equals(p.getStatus()))
                    .anyMatch(p -> {
                        Long partnerUserId = p.getUserId().equals(userId) ? p.getPartnerId() : p.getUserId();
                        List<Goal> partnerGoals = goalMapper.selectByUserId(partnerUserId);
                        return partnerGoals.stream().anyMatch(g -> isSameGoalCategory(goalType, g.getType()));
                    });

            if (hasPartner) {
                checkin.setStatus("pending");
                checkin.setProofImage(proofImage);
                checkin.setProofContent(proofContent);
                saveSubmission(checkin, existing);
                for (Partner p : partners) {
                    if ("accepted".equals(p.getStatus())) {
                        Long partnerUserId = p.getUserId().equals(userId) ? p.getPartnerId() : p.getUserId();
                        List<Goal> pGoals = goalMapper.selectByUserId(partnerUserId);
                        boolean typeMatch = pGoals.stream().anyMatch(g -> isSameGoalCategory(goalType, g.getType()));
                        if (typeMatch) {
                            notificationService.sendNotification(partnerUserId, userId, "checkin_review",
                                    "打卡待审核", "你的搭子提交了新的打卡凭证，快去审核吧！", "checkin", checkin.getId());
                            // 发送打卡凭证私信（含图片URL供前端展示）
                            String msgContent = "📸 打卡凭证";
                            if (proofContent != null && !proofContent.isEmpty()) {
                                msgContent += "\n📝 " + proofContent;
                            }
                            if (proofImage != null && !proofImage.isEmpty()) {
                                msgContent += "\n[IMG]" + proofImage + "[/IMG]";
                            }
                            Message msg = new Message();
                            msg.setFromUserId(userId);
                            msg.setToUserId(partnerUserId);
                            msg.setContent(msgContent);
                            msg.setType("checkin_proof");
                            msg.setRelatedId(checkin.getId());
                            messageMapper.insert(msg);
                        }
                    }
                }
            } else {
                checkin.setStatus("approved");
                saveSubmission(checkin, existing);
                updateGoalStatsAndAchievement(userId, goalId);
            }

            return checkin;
        }

        throw new RuntimeException("缺少goalId或taskId参数");
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getCheckinDates(Long userId, Integer year, Integer month) {
        return checkinMapper.selectCheckinDates(userId, year, month);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Checkin> getTodayTasks(Long userId) {
        List<Checkin> tasks = checkinMapper.selectTodayTasks(userId, LocalDate.now());
        System.out.println("[Checkin] 用户" + userId + "今日任务: 共" + tasks.size() + "个");
        for (Checkin t : tasks) {
            System.out.println("[Checkin]   → id=" + t.getId()
                    + " goalId=" + t.getGoalId()
                    + " taskName=" + t.getTaskName()
                    + " taskDate=" + t.getTaskDate()
                    + " completed=" + t.getCompleted()
                    + " userId=" + t.getUserId());
        }
        return tasks;
    }

    @Override
    @Transactional(readOnly = true)
    public Integer getStreak(Long userId, Long goalId) {
        return checkinMapper.countStreak(userId, goalId, LocalDate.now());
    }

    @Override
    @Transactional(readOnly = true)
    public Integer getTotalCheckins(Long userId, Long goalId) {
        return checkinMapper.countTotal(userId, goalId);
    }

    /**
     * 更新计划的已完成天数
     */
    private void updatePlanCompletedDays(Long planId) {
        Integer completedDays = planTaskMapper.countCompletedDays(planId);
        planMapper.updateCompletedDays(planId, completedDays == null ? 0 : completedDays);
    }

    private void saveSubmission(Checkin checkin, Checkin existing) {
        if (existing == null) {
            checkinMapper.insert(checkin);
        } else {
            checkin.setId(existing.getId());
            if (checkinMapper.resubmitRejected(checkin) != 1) {
                throw new RuntimeException("打卡状态已变化，请刷新后重试");
            }
        }
    }

    private void completeTask(PlanTask task) {
        if (task.getCompleted() == -1) {
            planTaskMapper.resetTaskCompletion(task.getId());
        } else {
            planTaskMapper.updateCompleted(task.getId(), 1);
        }
        updatePlanCompletedDays(task.getPlanId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Checkin> getRecentCheckins(Long userId, Integer limit) {
        return checkinMapper.selectRecentByUserId(userId, limit);
    }

    /**
     * 更新目标打卡统计 + 触发成就（approved 后调用）
     */
    private Goal requireActiveOwnedGoal(Long userId, Long goalId) {
        Goal goal = goalMapper.selectById(goalId);
        if (goal == null || !userId.equals(goal.getUserId())) {
            throw new RuntimeException("目标不存在或无权操作");
        }
        if (!"active".equals(goal.getStatus())) {
            throw new RuntimeException("目标已结束，无法打卡");
        }
        return goal;
    }

    private void updateGoalStatsAndAchievement(Long userId, Long goalId) {
        if (goalId != null) {
            Goal goal = goalMapper.selectById(goalId);
            if (goal == null || !userId.equals(goal.getUserId())) {
                throw new RuntimeException("目标不存在或无权操作");
            }
            Integer streak = checkinMapper.countStreak(userId, goalId, LocalDate.now());
            Integer total = checkinMapper.countTotal(userId, goalId);
            goalMapper.updateCheckinStats(goalId, streak, total);
        }
        achievementService.checkAndUnlock(userId, "checkin");
    }

    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Checkin reviewCheckin(Long userId, Long checkinId, boolean approved, String comment) {
        Checkin checkin = checkinMapper.selectById(checkinId);
        if (checkin == null) {
            throw new RuntimeException("打卡记录不存在");
        }
        goalMapper.lockById(checkin.getGoalId());
        checkin = checkinMapper.selectById(checkinId);
        if (checkin == null || !"pending".equals(checkin.getStatus())) {
            throw new RuntimeException("该打卡已被审核");
        }
        if (!canReviewCheckin(userId, checkin)) {
            throw new RuntimeException("你不是该目标的搭子，无权审核");
        }

        String newStatus = approved ? "approved" : "rejected";
        if (checkinMapper.updateStatus(checkinId, newStatus, userId, comment) != 1) {
            throw new RuntimeException("该打卡已被审核，请刷新后重试");
        }
        if (checkin.getTaskId() != null) {
            PlanTask task = planTaskMapper.selectById(checkin.getTaskId());
            if (task == null) throw new RuntimeException("任务不存在");
            Plan plan = planMapper.selectById(task.getPlanId());
            if (plan == null || !checkin.getUserId().equals(plan.getUserId())
                    || !checkin.getGoalId().equals(plan.getGoalId())) {
                throw new RuntimeException("任务归属异常");
            }
            if (approved) {
                completeTask(task);
            } else {
                planTaskMapper.updateCompleted(task.getId(), 0);
                updatePlanCompletedDays(task.getPlanId());
            }
        }

        // 更新关联的打卡凭证私信内容
        try {
            List<Message> messages = messageMapper.selectConversation(checkin.getUserId(), userId, 0, 200);
            for (Message m : messages) {
                if ("checkin_proof".equals(m.getType()) && checkinId.equals(m.getRelatedId())) {
                    String updatedContent = m.getContent();
                    if (approved) {
                        updatedContent += "\n\n✅ 已确认打卡";
                    } else {
                        updatedContent += "\n\n❌ 打卡未通过" + (comment != null ? "：" + comment : "");
                    }
                    Message update = new Message();
                    update.setId(m.getId());
                    update.setContent(updatedContent);
                    update.setType("checkin_confirmed");
                    messageMapper.updateContent(update);
                    break;
                }
            }
        } catch (Exception e) {
            System.out.println("[Checkin] 更新打卡凭证私信失败: " + e.getMessage());
        }

        if (approved) {
            updateGoalStatsAndAchievement(checkin.getUserId(), checkin.getGoalId());
            notificationService.sendNotification(checkin.getUserId(), userId, "checkin_approved",
                    "打卡通过", "你的搭子审核通过了你的打卡！", "checkin", checkinId);
        } else {
            notificationService.sendNotification(checkin.getUserId(), userId, "checkin_rejected",
                    "打卡被驳回", "你的打卡未通过审核" + (comment != null ? "：" + comment : ""), "checkin", checkinId);
        }

        return checkinMapper.selectById(checkinId);
    }

    /** 列表和审核操作共用授权规则：实际搭子、目标归属、审核人的目标类别。 */
    private boolean canReviewCheckin(Long reviewerId, Checkin checkin) {
        if (reviewerId.equals(checkin.getUserId()) || checkin.getGoalId() == null) return false;
        Goal goal = goalMapper.selectById(checkin.getGoalId());
        if (goal == null || !checkin.getUserId().equals(goal.getUserId())) return false;
        boolean actualPartner = partnerMapper.selectByUserId(reviewerId).stream()
                .filter(p -> "accepted".equals(p.getStatus()))
                .anyMatch(p -> (reviewerId.equals(p.getUserId()) && checkin.getUserId().equals(p.getPartnerId()))
                        || (reviewerId.equals(p.getPartnerId()) && checkin.getUserId().equals(p.getUserId())));
        if (!actualPartner) return false;
        PlanTask task = checkin.getTaskId() == null ? null : planTaskMapper.selectById(checkin.getTaskId());
        String type = task != null && task.getTaskType() != null ? task.getTaskType() : goal.getType();
        return goalMapper.selectByUserId(reviewerId).stream()
                .anyMatch(g -> isSameGoalCategory(type, g.getType()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Checkin> getPendingForReview(Long userId) {
        return checkinMapper.selectPendingForReview(userId).stream()
                .filter(c -> canReviewCheckin(userId, c)).toList();
    }

    @Override
    @Transactional
    public java.util.Map<String, Object> checkStreakBreak(Long userId) {
        System.out.println("========== [StreakBreak] 开始检查用户 " + userId + " 的断卡情况 ==========");

        // 查询用户所有活跃计划
        List<Plan> activePlans = planMapper.selectByUserId(userId).stream()
                .filter(p -> "active".equals(p.getStatus()))
                .toList();

        if (activePlans.isEmpty()) {
            System.out.println("[StreakBreak] 用户 " + userId + " 无活跃计划，跳过检查");
            return null;
        }

        System.out.println("[StreakBreak] 用户 " + userId + " 有 " + activePlans.size() + " 个活跃计划");

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("hasStreakBreak", false);
        result.put("affectedPlans", new java.util.ArrayList<>());
        result.put("supplementTasks", new java.util.ArrayList<>());

        for (Plan plan : activePlans) {
            System.out.println("[StreakBreak] ---- 检查计划 " + plan.getId() + " [" + plan.getName() + "] ----");

            // 0. 清理该计划中 task_date < 今天的旧补发任务（脏数据修复）
            try {
                int deleted = planTaskMapper.deleteExpiredSupplementTasks(plan.getId(), LocalDate.now());
                if (deleted > 0) {
                    System.out.println("[StreakBreak] 计划" + plan.getId() + " 清理了 " + deleted + " 个过期补发任务（task_date < 今天）");
                }
            } catch (Exception e) {
                System.out.println("[StreakBreak] 清理过期补发任务失败: " + e.getMessage());
            }

            // 1. 先标记过期未完成任务为断卡（completed → -1）
            try {
                List<PlanTask> incompletePastTasks = planTaskMapper.selectIncompletePastDays(plan.getId(), LocalDate.now());
                if (incompletePastTasks != null && !incompletePastTasks.isEmpty()) {
                    for (PlanTask task : incompletePastTasks) {
                        planTaskMapper.updateStreakBreakStatus(task.getId(), -1, "⚠️", "#FF4D4F");
                    }
                    System.out.println("[StreakBreak] 计划" + plan.getId() + " 自动标记 " + incompletePastTasks.size() + " 个断卡任务:");
                    for (PlanTask task : incompletePastTasks) {
                        System.out.println("[StreakBreak]   → 第" + task.getDayNumber() + "天 " + task.getTaskName() + " (id=" + task.getId() + ", 原日期=" + task.getTaskDate() + ")");
                    }
                } else {
                    System.out.println("[StreakBreak] 计划" + plan.getId() + " 无过期未完成任务需要标记");
                }
            } catch (Exception e) {
                System.out.println("[StreakBreak] 标记断卡失败: " + e.getMessage());
            }

            // 2. 检查是否有断卡任务（completed = -1 且 task_date < 今天，排除补发任务）
            List<PlanTask> missedTasks = planTaskMapper.selectIncompletePastDaysExcludeSupplement(plan.getId(), LocalDate.now());

            if (missedTasks.isEmpty()) {
                System.out.println("[StreakBreak] 计划" + plan.getId() + " ✅ 无断卡情况，一切正常");
            } else {
                System.out.println("[StreakBreak] 计划" + plan.getId() + " ❌ 检测到 " + missedTasks.size() + " 个断卡任务:");
                for (PlanTask t : missedTasks) {
                    System.out.println("[StreakBreak]   ❌ 断卡: 第" + t.getDayNumber() + "天 [" + t.getTaskName() + "] (id=" + t.getId() + ", 原日期=" + t.getTaskDate() + ", completed=" + t.getCompleted() + ")");
                }

                result.put("hasStreakBreak", true);

                // 3. 自动触发补卡调整（创建补发任务）
                try {
                    System.out.println("[StreakBreak] 计划" + plan.getId() + " 自动触发 adjustPlan 创建补发任务...");
                    planService.adjustPlan(plan.getId());
                    System.out.println("[StreakBreak] 计划" + plan.getId() + " adjustPlan 执行完成");
                } catch (Exception e) {
                    System.out.println("[StreakBreak] adjustPlan 失败: " + e.getMessage());
                    e.printStackTrace();
                }

                // 4. 查询补发任务详情（补在哪几天）
                List<PlanTask> supplementTasks = planTaskMapper.selectSupplementTasks(plan.getId());

                System.out.println("[StreakBreak] 计划" + plan.getId() + " 当前补发任务列表 (" + (supplementTasks != null ? supplementTasks.size() : 0) + " 个):");
                if (supplementTasks != null) {
                    for (PlanTask st : supplementTasks) {
                        System.out.println("[StreakBreak]   🔄 补发: [" + st.getTaskName() + "] 补到第" + st.getDayNumber() + "天 (日期=" + st.getTaskDate() + ", id=" + st.getId() + ", completed=" + st.getCompleted() + ")");
                    }
                }

                java.util.Map<String, Object> planInfo = new java.util.HashMap<>();
                planInfo.put("planId", plan.getId());
                planInfo.put("planName", plan.getName());
                planInfo.put("missedCount", missedTasks.size());

                // 构建补发任务详情：原始天数 → 补到哪天
                java.util.List<java.util.Map<String, Object>> supplementDetails = new java.util.ArrayList<>();
                if (supplementTasks != null) {
                    for (PlanTask st : supplementTasks) {
                        java.util.Map<String, Object> detail = new java.util.HashMap<>();
                        detail.put("taskId", st.getId());
                        detail.put("taskName", st.getTaskName());
                        detail.put("taskDate", st.getTaskDate() != null ? st.getTaskDate().toString() : null);
                        detail.put("dayNumber", st.getDayNumber());
                        detail.put("completed", st.getCompleted());
                        // 从 description 提取原始天数
                        String desc = st.getTaskDescription();
                        if (desc != null && desc.contains("(补第") && desc.contains("天)")) {
                            try {
                                int start = desc.indexOf("(补第") + 3;
                                int end = desc.indexOf("天)", start);
                                detail.put("originalDay", Integer.parseInt(desc.substring(start, end)));
                            } catch (Exception ignored) {}
                        }
                        supplementDetails.add(detail);
                    }
                }
                planInfo.put("supplementTasks", supplementDetails);
                planInfo.put("supplementCount", supplementTasks != null ? supplementTasks.size() : 0);

                @SuppressWarnings("unchecked")
                java.util.List<java.util.Map<String, Object>> affectedPlans =
                    (java.util.List<java.util.Map<String, Object>>) result.get("affectedPlans");
                affectedPlans.add(planInfo);

                // 把补发任务也加到顶层列表，方便前端统一展示
                @SuppressWarnings("unchecked")
                java.util.List<java.util.Map<String, Object>> allSupplements =
                    (java.util.List<java.util.Map<String, Object>>) result.get("supplementTasks");
                allSupplements.addAll(supplementDetails);
            }
        }

        boolean hasBreak = (boolean) result.get("hasStreakBreak");
        if (hasBreak) {
            System.out.println("[StreakBreak] ========== 总结: 检测到断卡，已自动补发任务 ==========");
        } else {
            System.out.println("[StreakBreak] ========== 总结: 所有计划无断卡情况 ==========");
        }

        return result;
    }
}