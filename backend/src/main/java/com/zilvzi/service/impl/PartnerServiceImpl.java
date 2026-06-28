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
        // 获取当前用户信息
        User currentUser = userMapper.selectById(userId);
        if (currentUser == null) return new ArrayList<>();

        List<Map<String, Object>> candidates;

        if (goalType != null && !goalType.isEmpty()) {
            // 使用智能匹配候选查询：按目标类型 + 可选省份筛选，联查打卡率
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> matched = (List<Map<String, Object>>) (List<?>) userMapper.selectMatchCandidates(userId, goalType, currentUser.getProvince());
            candidates = matched;
        } else if (keyword != null && !keyword.trim().isEmpty()) {
            // 关键词搜索走原有逻辑
            List<User> users = userMapper.searchUsers(keyword.trim(), userId);
            candidates = new ArrayList<>();
            for (User u : users) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", u.getId());
                m.put("nickname", u.getNickname());
                m.put("avatar", u.getAvatar());
                m.put("level", u.getLevel());
                m.put("province", u.getProvince());
                m.put("city", u.getCity());
                m.put("district", u.getDistrict());
                m.put("checkinRate", 0);
                candidates.add(m);
            }
        } else {
            List<User> users = userMapper.selectAllExcept(userId);
            candidates = new ArrayList<>();
            for (User u : users) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", u.getId());
                m.put("nickname", u.getNickname());
                m.put("avatar", u.getAvatar());
                m.put("level", u.getLevel());
                m.put("province", u.getProvince());
                m.put("city", u.getCity());
                m.put("district", u.getDistrict());
                m.put("checkinRate", 0);
                candidates.add(m);
            }
        }

        // 排除已拉黑的用户
        List<Partner> blocked = partnerMapper.selectBlockedByUserId(userId);
        Set<Long> blockedIds = new HashSet<>();
        for (Partner b : blocked) blockedIds.add(b.getPartnerId());

        List<Map<String, Object>> result = new ArrayList<>();
        double myCheckinRate = currentUser.getLevel() > 0 ? currentUser.getLevel() * 10.0 : 30.0; // 粗略估计

        for (Map<String, Object> c : candidates) {
            Long candidateId = ((Number) c.get("id")).longValue();
            if (blockedIds.contains(candidateId)) continue;

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
            score += Math.max(0, 10 - Math.abs(currentUser.getLevel() - level) * 2);

            // 关系状态
            String relationStatus = "none";
            Partner existing = partnerMapper.selectByUserAndPartner(userId, candidateId);
            if (existing != null) {
                if ("accepted".equals(existing.getStatus())) relationStatus = "partner";
                else if ("pending".equals(existing.getStatus())) relationStatus = "pending";
            }

            Map<String, Object> map = new HashMap<>();
            map.put("id", candidateId);
            map.put("nickname", c.get("nickname"));
            map.put("avatar", c.get("avatar"));
            map.put("level", level);
            map.put("province", candProvince);
            map.put("city", candCity);
            map.put("district", c.get("district"));
            map.put("checkinRate", Math.round(candidateRate));
            map.put("matchScore", Math.min(score, 100));
            map.put("relationStatus", relationStatus);
            result.add(map);
        }

        result.sort((a, b) -> Integer.compare((int) b.get("matchScore"), (int) a.get("matchScore")));
        return result.subList(0, Math.min(result.size(), 20));
    }

    @Override
    public void sendRequest(Long userId, Long partnerId, Long goalId) {
        // 不能给自己发申请
        if (userId.equals(partnerId)) {
            throw new RuntimeException("不能给自己发送搭子申请");
        }

        // 检查是否被对方拉黑
        List<Partner> allRecords = partnerMapper.selectAllByUserAndPartner(userId, partnerId);
        for (Partner r : allRecords) {
            if ("blocked".equals(r.getStatus()) && r.getUserId().equals(partnerId)) {
                throw new RuntimeException("对方已将你拉黑，无法发送申请");
            }
        }

        // 查找已有的申请记录
        Partner existing = partnerMapper.selectByUserAndPartner(userId, partnerId);
        if (existing != null && !"blocked".equals(existing.getStatus())) {
            if ("rejected".equals(existing.getStatus())) {
                partnerMapper.updateStatus(existing.getId(), "pending");
                notificationService.sendNotification(
                    partnerId, userId, "partner_apply",
                    "搭子申请", "想成为你的搭子",
                    "partner", existing.getId()
                );
                return;
            }
            throw new RuntimeException("已发送申请或已是搭子");
        }

        Partner partner = new Partner();
        partner.setUserId(userId);
        partner.setPartnerId(partnerId);
        partner.setGoalId(goalId);
        partner.setStatus("pending");
        partnerMapper.insert(partner);

        notificationService.sendNotification(
            partnerId, userId, "partner_apply",
            "搭子申请", "想成为你的搭子",
            "partner", partner.getId()
        );
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
                    result.add(map);
                }
            }
        }

        return result;
    }

    @Override
    public void acceptRequest(Long requestId) {
        Partner partner = partnerMapper.selectById(requestId);
        if (partner == null) {
            throw new RuntimeException("申请不存在");
        }
        if (!"pending".equals(partner.getStatus())) {
            throw new RuntimeException("该申请已被处理");
        }

        partnerMapper.updateStatus(requestId, "accepted");

        // 通知申请者：搭子同意了你的申请
        User acceptor = userMapper.selectById(partner.getPartnerId());
        notificationService.sendNotification(
            partner.getUserId(), partner.getPartnerId(), "partner_accept",
            "申请已通过", acceptor.getNickname() + " 同意了你的搭子申请",
            "partner", requestId
        );

        // 搭子关系建立，双方都检查社交勋章
        achievementService.checkSocialAchievements(partner.getUserId());
        achievementService.checkSocialAchievements(partner.getPartnerId());
    }

    @Override
    public void rejectRequest(Long requestId) {
        Partner partner = partnerMapper.selectById(requestId);
        if (partner == null) {
            throw new RuntimeException("申请不存在");
        }
        if (!"pending".equals(partner.getStatus())) {
            throw new RuntimeException("该申请已被处理");
        }
        partnerMapper.updateStatus(requestId, "rejected");
        
        // 通知申请者：搭子拒绝了你的申请
        User rejector = userMapper.selectById(partner.getPartnerId());
        notificationService.sendNotification(
            partner.getUserId(), partner.getPartnerId(), "partner_reject",
            "申请被拒绝", rejector.getNickname() + " 拒绝了你的搭子申请",
            "partner", requestId
        );
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
    public void removePartner(Long userId, Long partnerId) {
        Partner partner = partnerMapper.selectByUserAndPartner(userId, partnerId);
        if (partner == null) {
            throw new RuntimeException("搭子关系不存在");
        }
        
        partnerMapper.deleteById(partner.getId());
        
        notificationService.sendNotification(
            partnerId, userId, "partner_remove",
            "搭子解除", "已解除与你的搭子关系",
            null, null
        );
    }

    @Override
    public void blockPartner(Long userId, Long partnerId) {
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
            if (!r.getUserId().equals(userId) && "pending".equals(r.getStatus())) {
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
    public void unblockPartner(Long userId, Long partnerId) {
        List<Partner> allRecords = partnerMapper.selectAllByUserAndPartner(userId, partnerId);
        for (Partner partner : allRecords) {
            if ("blocked".equals(partner.getStatus()) && partner.getUserId().equals(userId)) {
                String prev = partner.getPrevStatus();
                if (prev != null && ("accepted".equals(prev) || "pending".equals(prev))) {
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
