package com.zilvzi.service.impl;

import com.zilvzi.mapper.GoalMapper;
import com.zilvzi.pojo.Goal;
import com.zilvzi.service.GoalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 目标Service实现类
 */
@Service
public class GoalServiceImpl implements GoalService {

    @Autowired
    private GoalMapper goalMapper;

    @Override
    public Goal createGoal(Goal goal) {
        goal.setStatus("active");
        goal.setStreak(0);
        goal.setTotalCheckins(0);
        goalMapper.insert(goal);
        System.out.println("[Goal] 创建目标: id=" + goal.getId() + " userId=" + goal.getUserId()
                + " name=" + goal.getName() + " type=" + goal.getType() + " status=" + goal.getStatus());
        return goal;
    }

    @Override
    public Goal getGoalById(Long id) {
        Goal goal = goalMapper.selectById(id);
        System.out.println("[Goal] 查询目标: id=" + id + " → " + (goal != null
                ? "name=" + goal.getName() + " status=" + goal.getStatus() + " userId=" + goal.getUserId()
                : "null"));
        return goal;
    }

    @Override
    public List<Goal> getUserGoals(Long userId) {
        List<Goal> goals = goalMapper.selectByUserId(userId);
        System.out.println("[Goal] 用户" + userId + "目标列表: 共" + goals.size() + "个");
        for (Goal g : goals) {
            System.out.println("[Goal]   → id=" + g.getId() + " name=" + g.getName()
                    + " type=" + g.getType() + " status=" + g.getStatus()
                    + " streak=" + g.getStreak() + " totalCheckins=" + g.getTotalCheckins()
                    + " createTime=" + g.getCreateTime());
        }
        return goals;
    }

    @Override
    public List<Goal> getTodayGoals(Long userId) {
        List<Goal> goals = goalMapper.selectTodayGoals(userId, LocalDate.now());
        System.out.println("[Goal] 用户" + userId + "今日目标: 共" + goals.size() + "个");
        for (Goal g : goals) {
            System.out.println("[Goal]   → id=" + g.getId() + " name=" + g.getName()
                    + " status=" + g.getStatus());
        }
        return goals;
    }

    @Override
    public void updateGoal(Goal goal) {
        goalMapper.update(goal);
        System.out.println("[Goal] 更新目标: id=" + goal.getId() + " name=" + goal.getName()
                + " status=" + goal.getStatus());
    }

    @Override
    public void deleteGoal(Long id) {
        goalMapper.updateStatus(id, "deleted");
        System.out.println("[Goal] 删除目标(软删除): id=" + id);
    }

    @Override
    public void completeGoal(Long id) {
        goalMapper.updateStatus(id, "completed");
        System.out.println("[Goal] 完成目标: id=" + id);
    }

    @Override
    public void abandonGoal(Long id) {
        goalMapper.updateStatus(id, "abandoned");
        System.out.println("[Goal] 放弃目标: id=" + id);
    }
}