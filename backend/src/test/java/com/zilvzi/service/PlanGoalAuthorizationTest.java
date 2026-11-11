package com.zilvzi.service;

import com.zilvzi.mapper.GoalMapper;
import com.zilvzi.mapper.PlanMapper;
import com.zilvzi.pojo.Goal;
import com.zilvzi.pojo.Plan;
import com.zilvzi.service.impl.PlanServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlanGoalAuthorizationTest {
    GoalMapper goals = mock(GoalMapper.class);
    PlanMapper plans = mock(PlanMapper.class);
    AiService ai = mock(AiService.class);
    PlanCacheService cache = mock(PlanCacheService.class);
    PlanServiceImpl service = new PlanServiceImpl();
    PlanGoalAuthorizationTest() {
        ReflectionTestUtils.setField(service,"goalMapper",goals);
        com.zilvzi.mapper.UserMapper users = mock(com.zilvzi.mapper.UserMapper.class);
        ReflectionTestUtils.setField(service,"userMapper",users);
        when(users.selectByIdForUpdate(1L)).thenReturn(new com.zilvzi.pojo.User());
        ReflectionTestUtils.setField(service,"planMapper",plans);
        ReflectionTestUtils.setField(service,"aiService",ai);
        ReflectionTestUtils.setField(service,"planCacheService",cache);
    }
    Goal goal(long owner, String status) {
        Goal g = new Goal(); g.setId(20L); g.setUserId(owner); g.setStatus(status); return g;
    }
    @Test void foreignMissingAndInactiveGoalsAreRejectedBeforePlanLookupOrAi() {
        for (Goal goal : new Goal[]{null,goal(2,"active"),goal(1,"completed"),goal(1,"deleted")}) {
            when(goals.selectById(20L)).thenReturn(goal);
            assertThrows(RuntimeException.class, () -> service.generatePlan(1L,20L,Map.of("goalName","学习")));
        }
        verifyNoInteractions(plans,ai,cache);
        verify(goals,never()).insert(any());
    }
    @Test void ownedActiveGoalCanReuseItsPlan() {
        when(goals.selectById(20L)).thenReturn(goal(1,"active"));
        Plan p = new Plan(); p.setId(30L); p.setUserId(1L); p.setGoalId(20L); p.setStatus("active");
        when(plans.selectByUserId(1L)).thenReturn(List.of());
        when(plans.selectByGoalId(20L)).thenReturn(p);
        assertSame(p,service.generatePlan(1L,20L,Map.of("goalName","学习")));
        verifyNoInteractions(ai,cache);
    }
    @Test void legacyForeignPlanCannotBeReturnedFromOwnedGoal() {
        when(goals.selectById(20L)).thenReturn(goal(1,"active"));
        Plan p = new Plan(); p.setUserId(2L); p.setStatus("active");
        when(plans.selectByUserId(1L)).thenReturn(List.of());
        when(plans.selectByGoalId(20L)).thenReturn(p);
        assertThrows(RuntimeException.class, () -> service.generatePlan(1L,20L,Map.of("goalName","学习")));
        verify(plans,never()).insert(any());
        verifyNoInteractions(ai,cache);
    }
}
