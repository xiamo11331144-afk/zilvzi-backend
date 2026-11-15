package com.zilvzi.service;

import com.zilvzi.mapper.*;
import com.zilvzi.pojo.Plan;
import com.zilvzi.pojo.PlanTask;
import com.zilvzi.service.impl.CheckinServiceImpl;
import com.zilvzi.service.impl.PlanServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StreakBreakReminderTest {
    Plan plan() {
        Plan p = new Plan(); p.setId(10L); p.setUserId(1L); p.setStatus("active");
        p.setName("学习计划"); p.setTotalDays(10); p.setCreateTime(LocalDateTime.now().minusDays(5));
        return p;
    }
    List<PlanTask> missed() {
        List<PlanTask> list = new ArrayList<>();
        for (int i=1;i<=3;i++) {
            PlanTask task = new PlanTask(); task.setId((long)i); task.setDayNumber(i);
            task.setTaskName("原任务"+i); task.setCompleted(-1); task.setTaskDate(LocalDate.now().minusDays(6-i));
            list.add(task);
        }
        return list;
    }
    @Test void thresholdPausesSupplementWithoutMovingOriginalDates() {
        PlanServiceImpl service = new PlanServiceImpl();
        PlanMapper plans = mock(PlanMapper.class); PlanTaskMapper tasks = mock(PlanTaskMapper.class);
        NotificationService notifications = mock(NotificationService.class);
        ReflectionTestUtils.setField(service,"planMapper",plans);
        ReflectionTestUtils.setField(service,"planTaskMapper",tasks);
        ReflectionTestUtils.setField(service,"notificationService",notifications);
        ReflectionTestUtils.setField(service,"userMapper",mock(UserMapper.class));
        ReflectionTestUtils.setField(service,"partnerMapper",mock(PartnerMapper.class));
        Plan plan = plan(); List<PlanTask> original = missed();
        List<LocalDate> dates = original.stream().map(PlanTask::getTaskDate).toList();
        when(plans.selectById(10L)).thenReturn(plan);
        when(tasks.selectIncompletePastDaysExcludeSupplement(eq(10L),any())).thenReturn(original);
        service.adjustPlan(10L);
        verify(tasks,never()).singleInsert(any());
        verify(tasks,never()).batchInsert(any());
        assertEquals(dates, original.stream().map(PlanTask::getTaskDate).toList());
        verify(notifications).sendNotification(eq(1L),isNull(),eq("streak_break"),eq("计划调整提醒"),
                contains("补卡机制已暂停"),eq("plan"),eq(10L));
    }
    @Test void checkResponseCarriesPauseReasonInsteadOfClaimingSupplementsWereCreated() {
        CheckinServiceImpl service = new CheckinServiceImpl();
        PlanMapper plans = mock(PlanMapper.class); PlanTaskMapper tasks = mock(PlanTaskMapper.class);
        PlanService planService = mock(PlanService.class);
        ReflectionTestUtils.setField(service,"planMapper",plans);
        ReflectionTestUtils.setField(service,"planTaskMapper",tasks);
        ReflectionTestUtils.setField(service,"planService",planService);
        when(plans.selectByUserId(1L)).thenReturn(List.of(plan()));
        when(tasks.selectIncompletePastDaysExcludeSupplement(eq(10L),any())).thenReturn(missed());
        when(planService.getStreakBreakInfo(10L)).thenReturn(Map.of("feasible",false,
                "infeasibleReason","断卡任务占比过高","remainingDays",4));
        Map<String,Object> result = service.checkStreakBreak(1L);
        Map<?,?> affected = (Map<?,?>)((List<?>)result.get("affectedPlans")).get(0);
        assertEquals(true,result.get("hasStreakBreak"));
        assertEquals(true,affected.get("supplementPaused"));
        assertEquals("断卡任务占比过高",affected.get("infeasibleReason"));
        assertEquals(0,affected.get("supplementCount"));
        assertEquals(false,affected.get("adjustmentFailed"));
    }
}
