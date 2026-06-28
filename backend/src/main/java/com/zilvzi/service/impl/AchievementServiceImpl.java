package com.zilvzi.service.impl;

import com.zilvzi.mapper.AchievementMapper;
import com.zilvzi.mapper.CheckinMapper;
import com.zilvzi.mapper.UserMapper;
import com.zilvzi.mapper.PartnerMapper;
import com.zilvzi.mapper.CircleMemberMapper;
import com.zilvzi.pojo.Achievement;
import com.zilvzi.pojo.User;
import com.zilvzi.service.AchievementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 成就Service实现类
 */
@Service
public class AchievementServiceImpl implements AchievementService {

    @Autowired
    private AchievementMapper achievementMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CheckinMapper checkinMapper;

    @Autowired
    private PartnerMapper partnerMapper;

    @Autowired
    private CircleMemberMapper circleMemberMapper;

    // 勋章定义（统一唯一来源，前端需与此同步）
    private static final Map<String, MedalDef> MEDAL_DEFS = new LinkedHashMap<>();
    static {
        MEDAL_DEFS.put("first_checkin", new MedalDef("初次打卡", "✓", "#4A90D9", "完成第一次打卡", 1));
        MEDAL_DEFS.put("streak_3",     new MedalDef("三日坚持", "3", "#52C41A", "连续打卡3天", 3));
        MEDAL_DEFS.put("streak_7",     new MedalDef("一周达人", "7", "#FAAD14", "连续打卡7天", 7));
        MEDAL_DEFS.put("streak_21",    new MedalDef("习惯养成", "21", "#FF6B6B", "连续打卡21天", 21));
        MEDAL_DEFS.put("streak_100",   new MedalDef("百日坚持", "100", "#722ED1", "连续打卡100天", 100));
        MEDAL_DEFS.put("total_30",     new MedalDef("月度之星", "30", "#13C2C2", "累计打卡30天", 30));
        MEDAL_DEFS.put("total_100",    new MedalDef("百次打卡", "百", "#FAAD14", "累计打卡100次", 100));
        MEDAL_DEFS.put("total_365",    new MedalDef("年度坚持", "365", "#FF6B6B", "累计打卡365天", 365));
        MEDAL_DEFS.put("early_bird",   new MedalDef("早起鸟儿", "早", "#52C41A", "凌晨5点前打卡", 0));
        MEDAL_DEFS.put("night_owl",    new MedalDef("夜猫子", "夜", "#722ED1", "晚上11点后打卡", 0));
        MEDAL_DEFS.put("partner_5",    new MedalDef("社交达人", "友", "#13C2C2", "拥有5个搭子", 5));
        MEDAL_DEFS.put("circle_3",     new MedalDef("圈子达人", "圈", "#4A90D9", "加入3个圈子", 3));
    }

    /** 供前端获取完整勋章定义列表 */
    public static Map<String, MedalDef> getMedalDefs() {
        return Collections.unmodifiableMap(MEDAL_DEFS);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Achievement> getUserAchievements(Long userId) {
        return achievementMapper.selectByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getUserLevelInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        Map<String, Object> info = new HashMap<>();
        int level = Math.max(user.getLevel(), 1);
        int exp = Math.max(user.getExperience(), 0);
        int nextLevelExp = getExpForLevel(level);

        info.put("level", level);
        info.put("levelTitle", getLevelTitle(level));
        info.put("currentExp", exp);
        info.put("nextLevelExp", nextLevelExp);
        info.put("expPercent", nextLevelExp > 0 ? Math.min((int) ((double) exp / nextLevelExp * 100), 100) : 0);

        // 统计数据
        Integer totalCheckins = checkinMapper.countTotalCheckins(userId);
        info.put("totalCheckins", totalCheckins != null ? totalCheckins : 0);

        Integer streak = checkinMapper.countStreak(userId, null, LocalDate.now());
        info.put("streakDays", streak != null ? streak : 0);

        List<Achievement> achievements = achievementMapper.selectByUserId(userId);
        info.put("medals", achievements.size());

        return info;
    }

    private String getLevelTitle(int level) {
        if (level < 3) return "自律新手";
        if (level < 5) return "自律达人";
        if (level < 8) return "自律高手";
        if (level < 10) return "自律大师";
        if (level < 15) return "自律王者";
        return "自律传说";
    }

    private int getExpForLevel(int level) {
        return Math.max(level, 1) * 500;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRankingList(Integer limit) {
        // 使用内联 streak 的优化查询，避免 N+1
        String today = LocalDate.now().toString();
        List<Map> rows = userMapper.selectRankingWithStreak(limit, today);
        List<Map<String, Object>> result = new ArrayList<>();

        int rank = 1;
        for (Map row : rows) {
            Map<String, Object> map = new HashMap<>();
            map.put("rank", rank++);
            map.put("id", ((Number) row.get("userId")).longValue());
            map.put("nickname", row.get("nickname"));
            map.put("avatar", row.get("avatar"));
            map.put("level", row.get("level"));
            map.put("totalDays", row.get("totalDays"));
            map.put("streakDays", row.get("streakDays"));
            result.add(map);
        }

        return result;
    }

    @Override
    @Transactional
    public void checkAndUnlock(Long userId, String type) {
        User user = userMapper.selectById(userId);
        if (user == null) return;

        Integer totalCheckins = checkinMapper.countTotalCheckins(userId);
        if (totalCheckins == null) totalCheckins = 0;

        if ("checkin".equals(type)) {
            // 第一次打卡
            if (totalCheckins >= MEDAL_DEFS.get("first_checkin").threshold) {
                unlockAchievement(userId, "first_checkin");
            }
            // 累计打卡
            if (totalCheckins >= MEDAL_DEFS.get("total_30").threshold) {
                unlockAchievement(userId, "total_30");
            }
            if (totalCheckins >= MEDAL_DEFS.get("total_100").threshold) {
                unlockAchievement(userId, "total_100");
            }
            if (totalCheckins >= MEDAL_DEFS.get("total_365").threshold) {
                unlockAchievement(userId, "total_365");
            }

            // 连续打卡
            Integer streakDays = checkinMapper.countStreak(userId, null, LocalDate.now());
            if (streakDays == null) streakDays = 0;
            if (streakDays >= MEDAL_DEFS.get("streak_3").threshold) unlockAchievement(userId, "streak_3");
            if (streakDays >= MEDAL_DEFS.get("streak_7").threshold) unlockAchievement(userId, "streak_7");
            if (streakDays >= MEDAL_DEFS.get("streak_21").threshold) unlockAchievement(userId, "streak_21");
            if (streakDays >= MEDAL_DEFS.get("streak_100").threshold) unlockAchievement(userId, "streak_100");

            // 早起鸟成就（凌晨0~5点打卡）
            int hour = LocalDateTime.now().getHour();
            if (hour < 5) unlockAchievement(userId, "early_bird");

            // 夜猫子成就（23点后打卡）
            if (hour >= 23) unlockAchievement(userId, "night_owl");

            // 增加经验值
            addExperience(userId, 10);
            // 注意：社交类勋章不在打卡路径调用，仅在搭子/圈子变化时触发
        }
    }

    @Override
    @Transactional
    public void checkSocialAchievements(Long userId) {
        // 社交达人成就（拥有5个搭子）
        int partnerCount = partnerMapper.selectByUserId(userId).size();
        if (partnerCount >= MEDAL_DEFS.get("partner_5").threshold) {
            unlockAchievement(userId, "partner_5");
        }

        // 圈子达人成就（加入3个圈子）
        int circleCount = circleMemberMapper.selectByUserId(userId).size();
        if (circleCount >= MEDAL_DEFS.get("circle_3").threshold) {
            unlockAchievement(userId, "circle_3");
        }
    }

    /**
     * 解锁单个勋章 —— INSERT IGNORE 保证幂等，affected=0 表示已存在
     */
    private void unlockAchievement(Long userId, String medalKey) {
        MedalDef def = MEDAL_DEFS.get(medalKey);
        if (def == null) return;

        // 先做内存层快速跳过（减少无意义 INSERT）
        if (achievementMapper.countByUserIdAndName(userId, def.name) > 0) return;

        Achievement achievement = new Achievement();
        achievement.setUserId(userId);
        achievement.setMedalName(def.name);
        achievement.setMedalIcon(def.icon);
        achievement.setMedalColor(def.color);
        achievement.setCondition(def.condition);
        achievement.setUnlockTime(LocalDateTime.now());

        // INSERT IGNORE：UNIQUE 约束兜底，返回 0 表示已存在
        int affected = achievementMapper.insert(achievement);
        if (affected > 0) {
            System.out.println("[Achievement] 用户" + userId + " 解锁勋章: " + def.name);
            addExperience(userId, 50);
        }
    }

    private void addExperience(Long userId, int exp) {
        User user = userMapper.selectById(userId);
        if (user == null) return;

        int newExp = user.getExperience() + exp;
        int level = Math.max(user.getLevel(), 1);
        int nextLevelExp = getExpForLevel(level);

        while (newExp >= nextLevelExp) {
            newExp -= nextLevelExp;
            level++;
            nextLevelExp = getExpForLevel(level);
        }

        // 使用专用方法，仅更新 level + experience，不影响 role 等字段
        userMapper.updateExperience(userId, level, newExp);
    }

    // 勋章定义内部类
    public static class MedalDef {
        public final String name;
        public final String icon;
        public final String color;
        public final String condition;
        public final int threshold;

        MedalDef(String name, String icon, String color, String condition, int threshold) {
            this.name = name;
            this.icon = icon;
            this.color = color;
            this.condition = condition;
            this.threshold = threshold;
        }
    }
}