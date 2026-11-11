package com.zilvzi.service;

import com.zilvzi.mapper.*;
import com.zilvzi.pojo.*;
import com.zilvzi.service.impl.CheckinServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CheckinServiceTest {
    CheckinMapper checkins = mock(CheckinMapper.class);
    GoalMapper goals = mock(GoalMapper.class);
    PartnerMapper partners = mock(PartnerMapper.class);
    PlanTaskMapper tasks = mock(PlanTaskMapper.class);
    PlanMapper plans = mock(PlanMapper.class);
    AchievementService achievements = mock(AchievementService.class);
    NotificationService notifications = mock(NotificationService.class);
    MessageMapper messages = mock(MessageMapper.class);
    CheckinServiceImpl service = new CheckinServiceImpl();

    @BeforeEach void setup() {
        ReflectionTestUtils.setField(service, "checkinMapper", checkins);
        ReflectionTestUtils.setField(service, "goalMapper", goals);
        ReflectionTestUtils.setField(service, "partnerMapper", partners);
        ReflectionTestUtils.setField(service, "planTaskMapper", tasks);
        ReflectionTestUtils.setField(service, "planMapper", plans);
        ReflectionTestUtils.setField(service, "achievementService", achievements);
        ReflectionTestUtils.setField(service, "notificationService", notifications);
        ReflectionTestUtils.setField(service, "messageMapper", messages);
    }
    Goal goal(long id, long owner, String type) {
        Goal g = new Goal(); g.setId(id); g.setUserId(owner); g.setType(type); g.setStatus("active"); return g;
    }
    Checkin pending(long owner) {
        Checkin c = new Checkin(); c.setId(100L); c.setUserId(owner); c.setGoalId(20L); c.setStatus("pending");
        when(checkins.selectById(100L)).thenReturn(c);
        when(goals.selectById(20L)).thenReturn(goal(20, owner, "study"));
        return c;
    }
    void relationship(long first, long second, String status) {
        Partner p = new Partner(); p.setUserId(first); p.setPartnerId(second); p.setStatus(status);
        when(partners.selectByUserId(1L)).thenReturn(List.of(p));
        when(goals.selectByUserId(1L)).thenReturn(List.of(goal(10, 1, "学习提升")));
    }
    @Test void goalCheckinRejectsMissingForeignDeletedAndInactiveGoalsBeforeAnyWrite() {
        for (Goal g : new Goal[]{null, goal(20,2,"study"), goal(20,1,"study"), goal(20,1,"study")}) {
            if (g != null && g.getUserId().equals(1L)) g.setStatus("completed");
            when(goals.selectById(20L)).thenReturn(g);
            assertThrows(RuntimeException.class, () -> service.doCheckin(1L,20L,null,null,null,null,null));
        }
        verify(checkins, never()).insert(any());
        verify(goals, never()).updateCheckinStats(anyLong(),any(),any());
        verifyNoInteractions(partners, notifications, achievements);
    }
    @Test void activeOwnedGoalCanBeCheckedInWithoutPartner() {
        when(goals.selectById(20L)).thenReturn(goal(20,1,"study"));
        Checkin result = service.doCheckin(1L,20L,null,"学习",null,null,null);
        assertEquals("approved",result.getStatus());
        assertEquals(1L,result.getUserId());
        assertEquals(20L,result.getGoalId());
        verify(checkins).insert(result);
        verify(achievements).checkAndUnlock(1L,"checkin");
    }
    @Test void unrelatedOwnerCannotBeReviewedOrListedEvenWithSameCategoryPartner() {
        Checkin c = pending(3); relationship(1, 2, "accepted");
        when(checkins.selectPendingForReview(1L)).thenReturn(List.of(c));
        assertTrue(service.getPendingForReview(1L).isEmpty());
        assertThrows(RuntimeException.class, () -> service.reviewCheckin(1L,100L,true,null));
        verify(checkins, never()).updateStatus(anyLong(),anyString(),anyLong(),any());
        verifyNoInteractions(notifications, achievements);
    }
    @Test void bothRelationshipDirectionsAndCategoryAliasesAreAllowed() {
        Checkin c = pending(2);
        for (long first : new long[]{1,2}) {
            relationship(first, first == 1 ? 2 : 1, "accepted");
            when(checkins.selectPendingForReview(1L)).thenReturn(List.of(c));
            assertEquals(List.of(c),service.getPendingForReview(1L));
        }
    }
    @Test void selfReviewPendingRelationshipWrongCategoryAndForgedGoalAreDenied() {
        Checkin c = pending(2); relationship(1,2,"pending");
        when(checkins.selectPendingForReview(1L)).thenReturn(List.of(c));
        assertTrue(service.getPendingForReview(1L).isEmpty());
        relationship(1,2,"accepted");
        when(goals.selectByUserId(1L)).thenReturn(List.of(goal(10,1,"fitness")));
        assertTrue(service.getPendingForReview(1L).isEmpty());
        when(goals.selectByUserId(1L)).thenReturn(List.of(goal(10,1,"study")));
        when(goals.selectById(20L)).thenReturn(goal(20,3,"study"));
        assertTrue(service.getPendingForReview(1L).isEmpty());
        c.setUserId(1L);
        assertTrue(service.getPendingForReview(1L).isEmpty());
    }
}
