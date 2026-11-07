package com.zilvzi.service.impl;

import com.zilvzi.mapper.GoalMapper;
import com.zilvzi.mapper.PartnerMapper;
import com.zilvzi.mapper.UserMapper;
import com.zilvzi.pojo.Goal;
import com.zilvzi.pojo.Partner;
import com.zilvzi.pojo.User;
import com.zilvzi.service.NotificationService;
import com.zilvzi.service.AchievementService;
import com.zilvzi.service.PartnerService;
import com.zilvzi.service.MentorMatchingService;
import com.zilvzi.service.VipService;
import com.zilvzi.exception.BusinessException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 搭子Service实现类
 */
@Service
public class PartnerServiceImpl implements PartnerService {

    @Autowired
    private PartnerMapper partnerMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private GoalMapper goalMapper;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private AchievementService achievementService;

    @Autowired
    private MentorMatchingService mentorMatchingService;

    @Autowired
    private VipService vipService;

    /** 将目标类型标准化为统一中文 */
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

    @Override
    public List<Map<String, Object>> getMyPartners(Long userId) {
        List<Partner> partners = partnerMapper.selectByUserId(userId);
        List<Map<String, Object>> result = new ArrayList<>();

        // 收集所有搭子用户ID，批量查目标（避免 N+1）
        Set<Long> partnerUserIds = new HashSet<>();
        for (Partner p : partners) {
            partnerUserIds.add(p.getUserId().equals(userId) ? p.getPartnerId() : p.getUserId());
        }
        Map<Long, List<Goal>> goalsByUser = new HashMap<>();
        if (!partnerUserIds.isEmpty()) {
            List<Goal> allGoals = goalMapper.selectGoalsByUserIds(new ArrayList<>(partnerUserIds));
            for (Goal g : allGoals) {
                goalsByUser.computeIfAbsent(g.getUserId(), k -> new ArrayList<>()).add(g);
            }
        }

        for (Partner p : partners) {
            Map<String, Object> map = new HashMap<>();
            Long partnerUserId = p.getUserId().equals(userId) ? p.getPartnerId() : p.getUserId();
            User partner = userMapper.selectById(partnerUserId);
            if (partner != null) {
                map.put("id", p.getId());
                map.put("partnerId", partnerUserId);
                map.put("nickname", partner.getNickname());
                map.put("avatar", partner.getAvatar());
                map.put("goalId", p.getGoalId());
                map.put("status", p.getStatus());
                map.put("matchMode", p.getMatchMode());
                List<String> goalTypes = goalsByUser.getOrDefault(partnerUserId, List.of()).stream()
                        .map(g -> normalizeGoalType(g.getType()))
                        .filter(t -> t != null && !t.isEmpty())
                        .distinct()
                        .toList();
                map.put("goalTypes", goalTypes);
                result.add(map);
            }
        }

        return result;
    }

    @Override
    public List<Map<String, Object>> getMatchRecommendations(Long userId, String goalType, String keyword, String matchMode) {
        matchMode = MentorMatchingService.normalizeMode(matchMode);
        User currentUser = userMapper.selectById(userId);
        if (currentUser == null) return new ArrayList<>();
        double myCheckinRate = MentorMatchingService.number(userMapper.selectMentorStats(userId), "checkinRate");
        List<String> goalTypes = null;
        if (goalType != null && !goalType.isBlank()) {
            goalTypes = switch (normalizeGoalType(goalType)) {
                case "考试" -> List.of("exam", "考试", "考试备考");
                case "学习" -> List.of("study", "学习", "学习提升");
                case "运动" -> List.of("fitness", "运动", "健身", "健身运动", "锻炼", "体育");
                case "阅读" -> List.of("reading", "阅读", "阅读计划");
                case "技能" -> List.of("skill", "技能", "技能学习");
                case "习惯" -> List.of("habit", "习惯", "习惯养成");
                default -> List.of(goalType);
            };
        }
        List<Map<String, Object>> candidates = userMapper.selectMatchCandidates(userId, goalTypes,
                currentUser.getProvince(), keyword == null ? null : keyword.trim(),
                "lead".equals(matchMode) ? 1 : 0, myCheckinRate);
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> c : candidates) {
            Object mentorValue = c.get("mentorEnabled");
            boolean mentorEnabled = (mentorValue instanceof Number
                    && ((Number) mentorValue).intValue() == 1)
                    || Boolean.TRUE.equals(mentorValue);
            if ("lead".equals(matchMode) && !mentorEnabled) continue;
            Long candidateId = ((Number) c.get("id")).longValue();

            int score = 0;

            // 1. 目标类型匹配 × 40（selectMatchCandidates 已按 goalType 筛选，天然匹配）
            if (goalType != null && !goalType.isEmpty()) {
                score += 40;
            }

            // 2. 打卡率兼容性 × 30
            double candidateRate = c.get("checkinRate") != null ? ((Number) c.get("checkinRate")).doubleValue() : 0;
            if ("lead".equals(matchMode)) {
                // 引领型：高打卡率配低打卡率
                score += (int) (30 * Math.min(candidateRate, 100) / 100.0);
            } else {
                // 共勉型：打卡率接近得分高
                double diff = Math.abs(myCheckinRate - candidateRate);
                score += (int) (30 * Math.max(0, 1 - diff / 100.0));
            }

            // 3. 地域接近性 × 20
            String candProvince = (String) c.get("province");
            String candCity = (String) c.get("city");
            if (currentUser.getProvince() != null && currentUser.getProvince().equals(candProvince)) {
                score += 10;
                if (currentUser.getCity() != null && currentUser.getCity().equals(candCity)) {
                    score += 10;
                }
            }

            // 4. 偏好/等级 × 10
            int level = c.get("level") != null ? ((Number) c.get("level")).intValue() : 1;
            score += Math.max(0, 10 - Math.abs((currentUser.getLevel() == null ? 1 : currentUser.getLevel()) - level) * 2);

            Map<String, Object> map = new HashMap<>();
            map.put("id", candidateId);
            map.put("nickname", c.get("nickname"));
            map.put("avatar", c.get("avatar"));
            map.put("level", level);
            map.put("province", candProvince);
            map.put("city", candCity);
            map.put("district", c.get("district"));
            map.put("checkinRate", candidateRate);
            map.put("approvedDays", c.get("approvedDays"));
            map.put("streak", c.get("streak"));
            map.put("remainingSlots", c.get("remainingSlots"));
            map.put("mentorEligible", MentorMatchingService.isEligible(c));
            map.put("mentorEnabled", mentorEnabled);
            map.put("matchScore", Math.min(score, 100));
            map.put("relationStatus", "none");
            result.add(map);
        }

        result.sort((a, b) -> Integer.compare((int) b.get("matchScore"), (int) a.get("matchScore")));
        return result.subList(0, Math.min(result.size(), 20));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void sendRequest(Long userId, Long partnerId, Long goalId, String matchMode) {
        matchMode = MentorMatchingService.normalizeMode(matchMode);
        mentorMatchingService.lockUsers(userId, partnerId);
        if (goalId != null) {
            Goal goal = goalMapper.selectById(goalId);
            if (goal == null || !userId.equals(goal.getUserId()) || !"active".equals(goal.getStatus())) {
                throw new IllegalArgumentException("只能关联自己的进行中目标");
            }
        }
        List<Partner> records = partnerMapper.selectAllByUserAndPartner(userId, partnerId);
        for (Partner record : records) {
            if ("blocked".equals(record.getStatus())) throw new IllegalArgumentException("存在拉黑关系，无法发送申请");
            if ("pending".equals(record.getStatus()) || "accepted".equals(record.getStatus())) {
                throw new IllegalArgumentException("已存在待处理申请或搭子关系");
            }
        }
        if ("lead".equals(matchMode)) {
            if (!vipService.isVip(userId)) throw new BusinessException(40301, "VIP_REQUIRED");
            mentorMatchingService.validateLead(userId, partnerId, null);
        }
        Partner partner = records.stream().filter(r -> "rejected".equals(r.getStatus()) && userId.equals(r.getUserId()))
                .findFirst().orElseGet(() -> records.stream().filter(r -> "rejected".equals(r.getStatus()))
                        .findFirst().orElseGet(Partner::new));
        partner.setUserId(userId);
        partner.setPartnerId(partnerId);
        partner.setGoalId(goalId);
        partner.setMatchMode(matchMode);
        partner.setStatus("pending");
        if (partner.getId() == null) partnerMapper.insert(partner);
        else if (partnerMapper.resetRequest(partner) != 1) throw new IllegalStateException("申请状态已变化，请刷新重试");
        notificationService.sendNotification(partnerId, userId, "partner_apply",
                "lead".equals(matchMode) ? "引领申请" : "搭子申请",
                "lead".equals(matchMode) ? "希望由你引领，一起坚持打卡" : "想成为你的搭子", "partner", partner.getId());
    }

    @Override
    public List<Map<String, Object>> getReceivedRequests(Long userId) {
        List<Partner> requests = partnerMapper.selectByPartnerId(userId);
        List<Map<String, Object>> result = new ArrayList<>();

        // 先查出被我拉黑的用户ID
        List<Partner> blockedList = partnerMapper.selectBlockedByUserId(userId);
        Set<Long> blockedUserIds = new HashSet<>();
        for (Partner b : blockedList) {
            blockedUserIds.add(b.getPartnerId());
        }

        for (Partner p : requests) {
            // 跳过被我拉黑的用户
            if (blockedUserIds.contains(p.getUserId())) continue;
            
            if ("pending".equals(p.getStatus()) || "rejected".equals(p.getStatus())) {
                Map<String, Object> map = new HashMap<>();
                User user = userMapper.selectById(p.getUserId());
                if (user != null) {
                    map.put("id", p.getId());
                    map.put("userId", p.getUserId());
                    map.put("nickname", user.getNickname());
                    map.put("avatar", user.getAvatar());
                    map.put("goalId", p.getGoalId());
                    map.put("status", p.getStatus());
                map.put("matchMode", p.getMatchMode());
                    result.add(map);
                }
            }
        }

        return result;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void acceptRequest(Long userId, Long requestId) {
        Partner partner = lockRequest(userId, requestId);
        if ("lead".equals(partner.getMatchMode())) {
            // 当前 pending 已预占一个名额；核验时排除自身，防止3/3时无法接受。
            mentorMatchingService.validateLead(partner.getUserId(), partner.getPartnerId(), requestId);
        }
        if (partnerMapper.updatePendingStatus(requestId, "accepted") != 1) {
            throw new IllegalStateException("申请已被处理，请刷新");
        }
        User acceptor = userMapper.selectById(userId);
        notificationService.sendNotification(partner.getUserId(), userId, "partner_accept",
                "申请已通过", acceptor.getNickname() + " 同意了你的搭子申请", "partner", requestId);
        achievementService.checkSocialAchievements(partner.getUserId());
        achievementService.checkSocialAchievements(partner.getPartnerId());
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void rejectRequest(Long userId, Long requestId) {
        Partner partner = lockRequest(userId, requestId);
        if (partnerMapper.updatePendingStatus(requestId, "rejected") != 1) {
            throw new IllegalStateException("申请已被处理，请刷新");
        }
        User rejector = userMapper.selectById(userId);
        notificationService.sendNotification(partner.getUserId(), userId, "partner_reject",
                "申请被拒绝", rejector.getNickname() + " 拒绝了你的搭子申请", "partner", requestId);
    }

    private Partner lockRequest(Long userId, Long requestId) {
        Partner beforeLock = partnerMapper.selectById(requestId);
        if (beforeLock == null || !userId.equals(beforeLock.getPartnerId())) {
            throw new IllegalArgumentException("申请不存在或无权操作");
        }
        mentorMatchingService.lockUsers(beforeLock.getUserId(), beforeLock.getPartnerId());
        Partner current = partnerMapper.selectById(requestId);
        if (current == null || !userId.equals(current.getPartnerId()) || !"pending".equals(current.getStatus())
                || !beforeLock.getUserId().equals(current.getUserId())) {
            throw new IllegalArgumentException("申请已被处理，请刷新");
        }
        return current;
    }

    @Override
    public void remindPartner(Long userId, Long partnerId) {
        notificationService.sendNotification(
            partnerId, userId, "checkin_remind",
            "打卡提醒", "提醒你记得打卡哦~",
            null, null
        );
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void removePartner(Long userId, Long partnerId) {
        mentorMatchingService.lockUsers(userId, partnerId);
        Partner partner = partnerMapper.selectByUserAndPartner(userId, partnerId);
        if (partner == null) {
            throw new RuntimeException("搭子关系不存在");
        }
        if ("blocked".equals(partner.getStatus())) {
            throw new IllegalArgumentException("存在拉黑关系，请由拉黑方先解除拉黑");
        }
        
        partnerMapper.deleteById(partner.getId());
        
        notificationService.sendNotification(
            partnerId, userId, "partner_remove",
            "搭子解除", "已解除与你的搭子关系",
            null, null
        );
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void blockPartner(Long userId, Long partnerId) {
        mentorMatchingService.lockUsers(userId, partnerId);
        if (userId.equals(partnerId)) {
            throw new RuntimeException("不能拉黑自己");
        }

        // 把对方发给我的申请标记为拒绝
        List<Partner> allRecords = partnerMapper.selectAllByUserAndPartner(userId, partnerId);
        Partner existingRecord = null;
        for (Partner r : allRecords) {
            if (r.getUserId().equals(userId) && r.getPartnerId().equals(partnerId)) {
                existingRecord = r;
            }
            if (!r.getUserId().equals(userId) && ("pending".equals(r.getStatus())
                    || ("lead".equals(r.getMatchMode()) && "accepted".equals(r.getStatus())))) {
                partnerMapper.updateStatus(r.getId(), "rejected");
            }
        }
        
        if (existingRecord != null) {
            // 保存拉黑前的状态（用于解黑恢复）
            partnerMapper.updatePrevStatus(existingRecord.getId(), existingRecord.getStatus());
            partnerMapper.updateStatus(existingRecord.getId(), "blocked");
        } else {
            // 没有记录，新建一条拉黑记录
            Partner blockRecord = new Partner();
            blockRecord.setUserId(userId);
            blockRecord.setPartnerId(partnerId);
            blockRecord.setStatus("blocked");
            blockRecord.setPrevStatus(null);
            partnerMapper.insert(blockRecord);
        }
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void unblockPartner(Long userId, Long partnerId) {
        mentorMatchingService.lockUsers(userId, partnerId);
        List<Partner> allRecords = partnerMapper.selectAllByUserAndPartner(userId, partnerId);
        for (Partner partner : allRecords) {
            if ("blocked".equals(partner.getStatus()) && partner.getUserId().equals(userId)) {
                String prev = partner.getPrevStatus();
                if ("lead".equals(partner.getMatchMode())) {
                    // 拉黑已释放名额，解黑不自动恢复引领关系；需重新申请并核验。
                    partnerMapper.updateStatus(partner.getId(), "rejected");
                    partnerMapper.updatePrevStatus(partner.getId(), null);
                } else if (prev != null && ("accepted".equals(prev) || "pending".equals(prev))) {
                    // 恢复拉黑前的状态
                    partnerMapper.updateStatus(partner.getId(), prev);
                } else {
                    // 无前置状态（纯拉黑记录），直接删除
                    partnerMapper.deleteById(partner.getId());
                }
                return;
            }
        }
    }

    @Override
    public List<Map<String, Object>> getBlockedPartners(Long userId) {
        List<Partner> blocked = partnerMapper.selectBlockedByUserId(userId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Partner p : blocked) {
            User user = userMapper.selectById(p.getPartnerId());
            if (user != null) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", p.getId());
                map.put("partnerId", p.getPartnerId());
                map.put("nickname", user.getNickname());
                map.put("avatar", user.getAvatar());
                result.add(map);
            }
        }
        return result;
    }
}
