package com.zilvzi.service;

import com.zilvzi.pojo.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CheckinLifecycleTest extends CheckinServiceTest {
    PlanTask task(long owner, int completed) {
        PlanTask t = new PlanTask(); t.setId(30L); t.setPlanId(40L); t.setCompleted(completed);
        t.setTaskType("study"); t.setTaskDate(java.time.LocalDate.now());
        when(tasks.selectById(30L)).thenReturn(t);
        Plan p = new Plan(); p.setId(40L); p.setUserId(owner); p.setGoalId(20L);
        when(plans.selectById(40L)).thenReturn(p);
        when(goals.selectById(20L)).thenReturn(goal(20,owner,"study"));
        return t;
    }
    void submissionPartner() {
        relationship(1,2,"accepted");
        when(goals.selectByUserId(2L)).thenReturn(java.util.List.of(goal(21,2,"学习提升")));
    }
    @Test void pendingTaskDoesNotCompleteOrChangeCounters() {
        task(1,0); submissionPartner();
        service.doCheckin(1L,null,30L,"内容",null,"proof","凭证");
        var capture = org.mockito.ArgumentCaptor.forClass(Checkin.class);
        verify(checkins).insert(capture.capture());
        assertEquals("pending",capture.getValue().getStatus());
        verify(tasks,never()).updateCompleted(anyLong(),anyInt());
        verify(plans,never()).updateCompletedDays(anyLong(),anyInt());
        verifyNoInteractions(achievements);
    }
    @Test void selfApprovedTaskCompletesAndUpdatesCounters() {
        task(1,0);
        service.doCheckin(1L,null,30L,null,null,null,null);
        verify(tasks).updateCompleted(30L,1);
        verify(plans).updateCompletedDays(40L,0);
        verify(achievements).checkAndUnlock(1L,"checkin");
    }
    @Test void duplicatePendingTaskHasNoRepeatedSideEffects() {
        task(1,0); Checkin c = pending(1); c.setTaskId(30L);
        when(checkins.selectByTaskAndDate(eq(30L),anyString())).thenReturn(c);
        assertSame(c,service.doCheckin(1L,null,30L,null,null,null,null));
        verify(checkins,never()).insert(any());
        verifyNoInteractions(notifications,achievements);
    }
    @Test void rejectedTaskReusesRecordAndAllowsNextDayProof() {
        PlanTask t=task(1,0); t.setTaskDate(java.time.LocalDate.now().minusDays(1)); submissionPartner();
        Checkin c=pending(1); c.setTaskId(30L); c.setStatus("rejected");
        when(checkins.selectByTaskAndDate(eq(30L),anyString())).thenReturn(c);
        when(checkins.resubmitRejected(any())).thenReturn(1);
        service.doCheckin(1L,null,30L,"new",null,"new-proof","补充");
        var capture=org.mockito.ArgumentCaptor.forClass(Checkin.class);
        verify(checkins).resubmitRejected(capture.capture());
        assertEquals(100L,capture.getValue().getId());
        assertEquals("pending",capture.getValue().getStatus());
        assertEquals("new-proof",capture.getValue().getProofImage());
        assertEquals(t.getTaskDate().toString(),capture.getValue().getDate());
        verify(checkins,never()).insert(any());
        verify(tasks,never()).updateCompleted(anyLong(),anyInt());
    }
    @Test void rejectedGoalReusesRecordWithoutPartnerAndApproves() {
        Checkin c=pending(1); c.setStatus("rejected");
        when(checkins.selectByUserAndDate(eq(1L),eq(20L),anyString())).thenReturn(c);
        when(checkins.resubmitRejected(any())).thenReturn(1);
        Checkin result=service.doCheckin(1L,20L,null,"补充",null,null,null);
        assertEquals(100L,result.getId()); assertEquals("approved",result.getStatus());
        verify(checkins,never()).insert(any());
        verify(achievements).checkAndUnlock(1L,"checkin");
    }
    @Test void failedResubmissionDoesNotCompleteTaskOrNotify() {
        task(1,0); Checkin c=pending(1); c.setStatus("rejected");
        when(checkins.selectByTaskAndDate(eq(30L),anyString())).thenReturn(c);
        assertThrows(RuntimeException.class,()->service.doCheckin(1L,null,30L,null,null,null,null));
        verify(tasks,never()).updateCompleted(anyLong(),anyInt());
        verifyNoInteractions(achievements,notifications);
    }
    @Test void approvedReviewCompletesTaskAndUpdatesCountersOnce() {
        Checkin c=pending(2); c.setTaskId(30L); task(2,0); relationship(1,2,"accepted");
        when(checkins.updateStatus(100L,"approved",1L,"通过")).thenReturn(1);
        service.reviewCheckin(1L,100L,true,"通过");
        verify(tasks).updateCompleted(30L,1);
        verify(plans).updateCompletedDays(40L,0);
        verify(achievements).checkAndUnlock(2L,"checkin");
    }
    @Test void rejectedReviewResetsLegacyCompletionWithoutGrantingAchievement() {
        Checkin c=pending(2); c.setTaskId(30L); task(2,1); relationship(1,2,"accepted");
        when(checkins.updateStatus(100L,"rejected",1L,"补充凭证")).thenReturn(1);
        service.reviewCheckin(1L,100L,false,"补充凭证");
        verify(tasks).updateCompleted(30L,0);
        verify(plans).updateCompletedDays(40L,0);
        verifyNoInteractions(achievements);
    }
    @Test void losingConcurrentReviewerCannotChangeCountersOrNotify() {
        pending(2); relationship(1,2,"accepted");
        when(checkins.updateStatus(100L,"approved",1L,null)).thenReturn(0);
        assertThrows(RuntimeException.class,()->service.reviewCheckin(1L,100L,true,null));
        verify(goals,never()).updateCheckinStats(anyLong(),any(),any());
        verifyNoInteractions(tasks,plans,achievements,notifications,messages);
    }
    @Test void reviewedWhileWaitingForGoalLockCannotBeReviewedAgain() {
        Checkin initial = pending(2);
        Checkin reviewed = new Checkin(); reviewed.setStatus("approved");
        when(checkins.selectById(100L)).thenReturn(initial, reviewed);
        assertThrows(RuntimeException.class, () -> service.reviewCheckin(1L,100L,true,null));
        var order = inOrder(checkins, goals);
        order.verify(checkins).selectById(100L);
        order.verify(goals).lockById(20L);
        order.verify(checkins).selectById(100L);
        verify(checkins,never()).updateStatus(anyLong(),anyString(),anyLong(),any());
        verifyNoInteractions(tasks,plans,achievements,notifications,messages);
    }
    @Test void counterFailurePropagatesToTransaction() {
        task(1,0);
        when(tasks.countCompletedDays(40L)).thenThrow(new IllegalStateException("counter failure"));
        assertThrows(IllegalStateException.class,()->service.doCheckin(1L,null,30L,null,null,null,null));
        verifyNoInteractions(achievements);
    }
}
