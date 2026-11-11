package com.zilvzi.service;

import com.zilvzi.mapper.PartnerMapper;
import com.zilvzi.mapper.UserMapper;
import com.zilvzi.pojo.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/** 引领规则的统一入口；写操作须先在事务中锁定双方用户行。 */
@Service
public class MentorMatchingService {
    public static final int CAPACITY = 3;
    public static final int REQUIRED_STREAK = 7;
    public static final int REQUIRED_DAYS = 24;
    public static final int REQUIRED_RATE = 80;

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private PartnerMapper partnerMapper;

    public static double number(Map<String, Object> values, String key) {
        Object value = values == null ? null : values.get(key);
        return value instanceof Number ? ((Number) value).doubleValue() : 0D;
    }

    public static boolean isEligible(Map<String, Object> stats) {
        return number(stats, "approvedDays") >= REQUIRED_DAYS
                && number(stats, "checkinRate") >= REQUIRED_RATE
                && number(stats, "streak") >= REQUIRED_STREAK;
    }

    public Map<String, Object> getStatus(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new IllegalArgumentException("用户不存在");
        Map<String, Object> stats = userMapper.selectMentorStats(userId);
        Map<String, Object> slots = partnerMapper.selectMentorCapacity(userId, null);
        Map<String, Object> result = new HashMap<>();
        result.put("enabled", Integer.valueOf(1).equals(user.getMentorEnabled()));
        result.put("eligible", isEligible(stats));
        result.put("approvedDays", (int) number(stats, "approvedDays"));
        result.put("checkinRate", number(stats, "checkinRate"));
        result.put("streak", (int) number(stats, "streak"));
        result.put("requiredDays", REQUIRED_STREAK);
        result.put("requiredApprovedDays", REQUIRED_DAYS);
        result.put("requiredRate", REQUIRED_RATE);
        result.put("capacity", CAPACITY);
        result.put("occupiedSlots", (int) number(slots, "occupiedSlots"));
        result.put("pendingCount", (int) number(slots, "pendingCount"));
        result.put("acceptedCount", (int) number(slots, "acceptedCount"));
        result.put("remainingSlots", Math.max(0, CAPACITY - (int) number(slots, "occupiedSlots")));
        List<String> unmet = new ArrayList<>();
        if (number(stats, "approvedDays") < REQUIRED_DAYS) unmet.add("近30天有效打卡不足" + REQUIRED_DAYS + "天");
        if (number(stats, "checkinRate") < REQUIRED_RATE) unmet.add("近30天有效打卡率不足" + REQUIRED_RATE + "%");
        if (number(stats, "streak") < REQUIRED_STREAK) unmet.add("当前连续有效打卡不足" + REQUIRED_STREAK + "天");
        boolean enabled = Integer.valueOf(1).equals(user.getMentorEnabled());
        String state = !enabled ? "disabled" : !isEligible(stats) ? "ineligible"
                : number(slots, "occupiedSlots") >= CAPACITY ? "full" : "available";
        result.put("recommendationState", state);
        result.put("recommendationAvailable", "available".equals(state));
        result.put("unmetRequirements", unmet);
        return result;
    }

    public String unavailableReason(Long applicantId, Long mentorId, Long excludeRequestId) {
        if (applicantId.equals(mentorId)) return "不能向自己申请引领";
        User mentor = userMapper.selectById(mentorId);
        if (mentor == null) return "用户不存在";
        if (!Integer.valueOf(1).equals(mentor.getMentorEnabled())) return "对方已关闭引领匹配";
        Map<String, Object> mentorStats = userMapper.selectMentorStats(mentorId);
        if (!isEligible(mentorStats)) return "对方当前未达到引领资格，请选择其他引领者";
        Map<String, Object> applicantStats = userMapper.selectMentorStats(applicantId);
        if (number(mentorStats, "checkinRate") <= number(applicantStats, "checkinRate")) {
            return "引领者近30天有效打卡率需高于申请人，可改选共勉型";
        }
        Map<String, Object> slots = partnerMapper.selectMentorCapacity(mentorId, excludeRequestId);
        if (number(slots, "occupiedSlots") >= CAPACITY) return "对方的3个引领名额已满，请稍后再试";
        return null;
    }

    public void validateLead(Long applicantId, Long mentorId, Long excludeRequestId) {
        String reason = unavailableReason(applicantId, mentorId, excludeRequestId);
        if (reason != null) throw new IllegalArgumentException(reason);
    }

    /** 固定主键顺序取锁，普通申请也取锁，避免两个方向同时创建重复关系。 */
    public void lockUsers(Long firstId, Long secondId) {
        if (firstId == null || secondId == null || firstId.equals(secondId)) {
            throw new IllegalArgumentException("请选择其他用户");
        }
        Long lower = Math.min(firstId, secondId);
        Long higher = Math.max(firstId, secondId);
        if (userMapper.selectByIdForUpdate(lower) == null || userMapper.selectByIdForUpdate(higher) == null) {
            throw new IllegalArgumentException("用户不存在");
        }
    }

    public static String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) return "mutual";
        if (!"mutual".equals(mode) && !"lead".equals(mode)) {
            throw new IllegalArgumentException("匹配模式仅支持mutual或lead");
        }
        return mode;
    }
}
