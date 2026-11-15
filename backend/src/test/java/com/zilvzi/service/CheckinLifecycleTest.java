package com.zilvzi.service;

import com.zilvzi.pojo.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CheckinLifecycleTest extends CheckinServiceTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"1,true", "2,true", "1,false", "2,false"})
    void differentPlanNamesAndTaskTypesStillAllowMatchingGoalPartnersToReview(long submitter, boolean approved) {
        long reviewer = submitter == 1 ? 2 : 1;
        PlanTask task = task(submitter, 0);
        task.setTaskType("练习");
        Goal submittedGoal = goal(20, submitter, "study");
        submittedGoal.setName(submitter == 1 ? "计算机二级C语言" : "计算机二级Python");
        when(goals.selectById(20L)).thenReturn(submittedGoal);
        Goal reviewerGoal = goal(21, reviewer, "学习提升");
        reviewerGoal.setName(submitter == 1 ? "计算机二级Python" : "计算机二级C语言");
        when(goals.selectByUserId(reviewer)).thenReturn(java.util.List.of(reviewerGoal));
        Partner relation = new Partner(); relation.setUserId(1L); relation.setPartnerId(2L);
        relation.setMatchMode("lead"); relation.setStatus("accepted");
        when(partners.selectByUserId(submitter)).thenReturn(java.util.List.of(relation));
        when(partners.selectByUserId(reviewer)).thenReturn(java.util.List.of(relation));
        doAnswer(invocation -> { ((Checkin)invocation.getArgument(0)).setId(100L); return 1; }).when(checkins).insert(any());

        assertTrue(service.requiresReview(submitter,30L));
        service.doCheckin(submitter,null,30L,null,null,"proof-photo",null);
        var record = org.mockito.ArgumentCaptor.forClass(Checkin.class);
        verify(checkins).insert(record.capture());
        Checkin pending = record.getValue();
        assertEquals("pending", pending.getStatus());
        when(checkins.selectById(100L)).thenReturn(pending);
        when(checkins.selectPendingForReview(reviewer)).thenReturn(java.util.List.of(pending));
        assertEquals(java.util.List.of(pending), service.getPendingForReview(reviewer));
        String status = approved ? "approved" : "rejected";
        when(checkins.updateStatus(100L,status,reviewer,null)).thenReturn(1);

        service.reviewCheckin(reviewer,100L,approved,null);

        verify(checkins).updateStatus(100L,status,reviewer,null);
        verify(tasks).updateCompleted(30L,approved ? 1 : 0);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(longs = {1, 2})
    void goalCategoryOverridesAiTaskTypeInBothLeadDirections(long submitter) {
        long reviewer = submitter == 1 ? 2 : 1;
        PlanTask task = task(submitter, 0);
        task.setTaskType("练习");
        when(goals.selectById(20L)).thenReturn(goal(20, submitter, "exam"));
        Partner relation = new Partner(); relation.setUserId(1L); relation.setPartnerId(2L);
        relation.setMatchMode("lead"); relation.setStatus("accepted");
        when(partners.selectByUserId(submitter)).thenReturn(java.util.List.of(relation));
        when(goals.selectByUserId(reviewer)).thenReturn(java.util.List.of(goal(21, reviewer, "考试备考")));
        assertTrue(service.requiresReview(submitter, 30L));
        assertThrows(IllegalArgumentException.class, () -> service.doCheckin(submitter,null,30L,null,null,null,null));
        verify(checkins,never()).insert(any());
        service.doCheckin(submitter,null,30L,null,null,"proof-photo",null);
        var record = org.mockito.ArgumentCaptor.forClass(Checkin.class);
        verify(checkins).insert(record.capture());
        assertEquals("pending",record.getValue().getStatus());
        var message = org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(messages).insert(message.capture());
        assertEquals(reviewer,message.getValue().getToUserId().longValue());
        verify(tasks,never()).updateCompleted(anyLong(),anyInt());
    }

    @Test void reviewRequirementDoesNotExposeOtherUsersTasks() {
        task(2,0);
        assertThrows(IllegalArgumentException.class, () -> service.requiresReview(1L,30L));
        verifyNoInteractions(partners);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"1,true", "2,true", "1,false", "2,false"})
    void neitherSideOfLeadRelationshipCanSubmitWithoutProof(long submitter, boolean taskSubmission) {
        long reviewer = submitter == 1 ? 2 : 1;
        Partner relation = new Partner();
        relation.setUserId(1L); relation.setPartnerId(2L);
        relation.setMatchMode("lead"); relation.setStatus("accepted");
        when(partners.selectByUserId(submitter)).thenReturn(java.util.List.of(relation));
        when(goals.selectByUserId(reviewer)).thenReturn(java.util.List.of(goal(21, reviewer, "学习")));
        if (taskSubmission) task(submitter, 0);
        else when(goals.selectById(20L)).thenReturn(goal(20, submitter, "study"));

        var error = assertThrows(IllegalArgumentException.class, () -> service.doCheckin(submitter,
                taskSubmission ? null : 20L, taskSubmission ? 30L : null, null, null, " ", null));
        assertTrue(error.getMessage().contains("凭证照片"));
        verify(checkins, never()).insert(any());
        verify(tasks, never()).updateCompleted(anyLong(), anyInt());
        verifyNoInteractions(notifications, messages, achievements);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"1,true", "2,true", "1,false", "2,false"})
    void leadRelationshipRequiresSameCategoryProofReviewInBothDirections(long submitter, boolean taskSubmission) {
        long reviewer = submitter == 1 ? 2 : 1;
        Partner relation = new Partner();
        relation.setUserId(1L); relation.setPartnerId(2L);
        relation.setMatchMode("lead"); relation.setStatus("accepted");
        when(partners.selectByUserId(submitter)).thenReturn(java.util.List.of(relation));
        when(goals.selectByUserId(reviewer)).thenReturn(java.util.List.of(goal(21, reviewer, "学习提升")));
        if (taskSubmission) task(submitter, 0);
        else when(goals.selectById(20L)).thenReturn(goal(20, submitter, "study"));
        doAnswer(invocation -> {
            Checkin record = invocation.getArgument(0);
            record.setId(100L);
            return 1;
        }).when(checkins).insert(any());

        service.doCheckin(submitter, taskSubmission ? null : 20L, taskSubmission ? 30L : null,
                "任务完成", null, "https://example.com/proof.png", "执行凭证");

        var record = org.mockito.ArgumentCaptor.forClass(Checkin.class);
        verify(checkins).insert(record.capture());
        assertEquals("pending", record.getValue().getStatus());
        assertEquals(submitter, record.getValue().getUserId().longValue());
        assertEquals("https://example.com/proof.png", record.getValue().getProofImage());
        var message = org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(messages).insert(message.capture());
        assertEquals(submitter, message.getValue().getFromUserId().longValue());
        assertEquals(reviewer, message.getValue().getToUserId().longValue());
        assertEquals(100L, message.getValue().getRelatedId());
        assertEquals("checkin_proof", message.getValue().getType());
        verify(notifications).sendNotification(eq(reviewer), eq(submitter), eq("checkin_review"),
                anyString(), anyString(), eq("checkin"), eq(100L));
        verify(tasks, never()).updateCompleted(anyLong(), anyInt());
        verifyNoInteractions(achievements);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(longs = {1, 2})
    void eitherSideOfLeadRelationshipCanReviewTheOtherSidesMatchingGoal(long submitter) {
        long reviewer = submitter == 1 ? 2 : 1;
        pending(submitter);
        Partner relation = new Partner();
        relation.setUserId(1L); relation.setPartnerId(2L);
        relation.setMatchMode("lead"); relation.setStatus("accepted");
        when(partners.selectByUserId(reviewer)).thenReturn(java.util.List.of(relation));
        when(goals.selectByUserId(reviewer)).thenReturn(java.util.List.of(goal(21, reviewer, "学习")));
        when(checkins.updateStatus(100L, "approved", reviewer, null)).thenReturn(1);

        service.reviewCheckin(reviewer, 100L, true, null);

        verify(checkins).updateStatus(100L, "approved", reviewer, null);
        verify(notifications).sendNotification(eq(submitter), eq(reviewer), eq("checkin_approved"),
                anyString(), anyString(), eq("checkin"), eq(100L));
    }

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
