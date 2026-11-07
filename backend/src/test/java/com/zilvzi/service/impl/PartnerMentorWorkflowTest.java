package com.zilvzi.service.impl;

import com.zilvzi.mapper.*;
import com.zilvzi.pojo.*;
import com.zilvzi.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PartnerMentorWorkflowTest {
    PartnerMapper partners;
    UserMapper users;
    GoalMapper goals;
    NotificationService notifications;
    AchievementService achievements;
    MentorMatchingService rules;
    VipService vip;
    PartnerServiceImpl service;

    @BeforeEach void setup() {
        partners=mock(PartnerMapper.class); users=mock(UserMapper.class); goals=mock(GoalMapper.class);
        notifications=mock(NotificationService.class); achievements=mock(AchievementService.class);
        rules=spy(new MentorMatchingService());
        vip=mock(VipService.class);
        when(vip.isVip(10L)).thenReturn(true);
        ReflectionTestUtils.setField(rules,"userMapper",users);
        ReflectionTestUtils.setField(rules,"partnerMapper",partners);
        service=new PartnerServiceImpl();
        ReflectionTestUtils.setField(service,"partnerMapper",partners);
        ReflectionTestUtils.setField(service,"userMapper",users);
        ReflectionTestUtils.setField(service,"goalMapper",goals);
        ReflectionTestUtils.setField(service,"notificationService",notifications);
        ReflectionTestUtils.setField(service,"achievementService",achievements);
        ReflectionTestUtils.setField(service,"mentorMatchingService",rules);
        ReflectionTestUtils.setField(service,"vipService",vip);
        User applicant=user(10,0); User mentor=user(20,1);
        when(users.selectByIdForUpdate(10L)).thenReturn(applicant);
        when(users.selectByIdForUpdate(20L)).thenReturn(mentor);
        when(users.selectById(10L)).thenReturn(applicant);
        when(users.selectById(20L)).thenReturn(mentor);
        when(users.selectMentorStats(10L)).thenReturn(Map.of("approvedDays",10,"streak",2,"checkinRate",33.3));
        when(users.selectMentorStats(20L)).thenReturn(Map.of("approvedDays",24,"streak",7,"checkinRate",80D));
        when(partners.selectAllByUserAndPartner(anyLong(),anyLong())).thenReturn(List.of());
    }
    static User user(long id,int enabled) { User u=new User(); u.setId(id);u.setMentorEnabled(enabled);u.setNickname("test");return u; }
    static Partner request(String status,String mode) {
        Partner p=new Partner();p.setId(99L);p.setUserId(10L);p.setPartnerId(20L);p.setStatus(status);p.setMatchMode(mode);return p;
    }
    void pending(String mode) { when(partners.selectById(99L)).thenReturn(request("pending",mode)); }
    void noRelationshipWrites() {
        verify(partners,never()).insert(any());
        verify(partners,never()).resetRequest(any());
        verify(partners,never()).updatePendingStatus(anyLong(),anyString());
        verifyNoInteractions(notifications,achievements);
    }

    @Test void leadApplicationLocksBeforeReadingCapacityAndPersistsMode() {
        when(partners.selectMentorCapacity(20L,null)).thenReturn(Map.of("occupiedSlots",2));
        service.sendRequest(10L,20L,null,"lead");
        InOrder order=inOrder(users,partners);
        order.verify(users).selectByIdForUpdate(10L);
        order.verify(users).selectByIdForUpdate(20L);
        order.verify(partners).selectAllByUserAndPartner(10L,20L);
        order.verify(partners).selectMentorCapacity(20L,null);
        ArgumentCaptor<Partner> saved=ArgumentCaptor.forClass(Partner.class);
        order.verify(partners).insert(saved.capture());
        assertEquals("lead",saved.getValue().getMatchMode());
        assertEquals("pending",saved.getValue().getStatus());
    }
    @Test void leadApplicationRequiresVipAndDoesNotWriteRelationship() {
        when(vip.isVip(10L)).thenReturn(false);
        com.zilvzi.exception.BusinessException error = assertThrows(com.zilvzi.exception.BusinessException.class,
                () -> service.sendRequest(10L,20L,null,"lead"));
        assertEquals(40301, error.getCode());
        assertEquals("VIP_REQUIRED", error.getMessage());
        verify(rules,never()).validateLead(anyLong(),anyLong(),any());
        noRelationshipWrites();
    }
    @Test void mutualApplicationIsFreeAndDoesNotCheckVip() {
        service.sendRequest(10L,20L,null,"mutual");
        verify(vip,never()).isVip(anyLong());
        verify(partners).insert(any(Partner.class));
    }
    @Test void existingLeadRelationshipIsRejectedBeforeVipLookup() {
        when(partners.selectAllByUserAndPartner(10L,20L)).thenReturn(List.of(request("accepted","lead")));
        assertThrows(IllegalArgumentException.class,()->service.sendRequest(10L,20L,null,"lead"));
        verify(vip,never()).isVip(anyLong());
        noRelationshipWrites();
    }
    @ParameterizedTest @ValueSource(strings={"full","equal","closed","expired"})
    void invalidMentorPreventsApplicationWithoutWrites(String reason) {
        switch(reason) {
            case "full" -> when(partners.selectMentorCapacity(20L,null)).thenReturn(Map.of("occupiedSlots",3));
            case "equal" -> when(users.selectMentorStats(10L)).thenReturn(Map.of("checkinRate",80D));
            case "closed" -> when(users.selectById(20L)).thenReturn(user(20,0));
            case "expired" -> when(users.selectMentorStats(20L)).thenReturn(Map.of("approvedDays",24,"streak",0,"checkinRate",80D));
        }
        assertThrows(IllegalArgumentException.class,()->service.sendRequest(10L,20L,null,"lead"));
        noRelationshipWrites();
    }
    @ParameterizedTest @ValueSource(strings={"pending","accepted","blocked"})
    void existingEffectiveRelationshipCannotBeAppliedAgain(String status) {
        when(partners.selectAllByUserAndPartner(10L,20L)).thenReturn(List.of(request(status,"mutual")));
        assertThrows(IllegalArgumentException.class,()->service.sendRequest(10L,20L,null,"lead"));
        noRelationshipWrites();
    }
    @ParameterizedTest @ValueSource(strings={"lead","mutual"})
    void resendingRejectedReverseRequestReplacesDirectionModeAndGoal(String mode) {
        Partner old=request("rejected", "lead".equals(mode)?"mutual":"lead");
        old.setUserId(20L);old.setPartnerId(10L);old.setGoalId(123L);
        when(partners.selectAllByUserAndPartner(10L,20L)).thenReturn(List.of(old));
        when(partners.resetRequest(any())).thenReturn(1);
        service.sendRequest(10L,20L,null,mode);
        ArgumentCaptor<Partner> saved=ArgumentCaptor.forClass(Partner.class);
        verify(partners).resetRequest(saved.capture());
        assertEquals(99L,saved.getValue().getId());
        assertEquals(10L,saved.getValue().getUserId());
        assertEquals(20L,saved.getValue().getPartnerId());
        assertEquals(mode,saved.getValue().getMatchMode());
        assertNull(saved.getValue().getGoalId());
        verify(partners,never()).insert(any());
    }
    @Test void rejectedResetRaceDoesNotSendSuccessNotification() {
        when(partners.selectAllByUserAndPartner(10L,20L)).thenReturn(List.of(request("rejected","lead")));
        when(partners.resetRequest(any())).thenReturn(0);
        assertThrows(IllegalStateException.class,()->service.sendRequest(10L,20L,null,"lead"));
        verifyNoInteractions(notifications);
    }
    @Test void cannotAttachSomeoneElsesGoal() {
        Goal goal=new Goal();goal.setUserId(20L);goal.setStatus("active");
        when(goals.selectById(7L)).thenReturn(goal);
        assertThrows(IllegalArgumentException.class,()->service.sendRequest(10L,20L,7L,"mutual"));
        noRelationshipWrites();
    }
    @Test void acceptsThirdReservedSlotByExcludingOwnPendingRequest() {
        pending("lead");
        when(partners.selectMentorCapacity(20L,null)).thenReturn(Map.of("occupiedSlots",3));
        when(partners.selectMentorCapacity(20L,99L)).thenReturn(Map.of("occupiedSlots",2));
        when(partners.updatePendingStatus(99L,"accepted")).thenReturn(1);
        service.acceptRequest(20L,99L);
        verify(vip,never()).isVip(anyLong());
        verify(rules).validateLead(10L,20L,99L);
        verify(partners).selectMentorCapacity(20L,99L);
        verify(partners,never()).selectMentorCapacity(20L,null);
        verify(achievements).checkSocialAchievements(10L);
        verify(achievements).checkSocialAchievements(20L);
        InOrder order=inOrder(users,partners);
        order.verify(partners).selectById(99L);
        order.verify(users).selectByIdForUpdate(10L);
        order.verify(users).selectByIdForUpdate(20L);
        order.verify(partners).selectById(99L);
        order.verify(partners).selectMentorCapacity(20L,99L);
        order.verify(partners).updatePendingStatus(99L,"accepted");
    }
    @ParameterizedTest @ValueSource(strings={"full","equal","closed","expired"})
    void acceptanceRevalidatesMentorEvenAfterApplicationWasCreated(String reason) {
        pending("lead");
        switch(reason) {
            case "full" -> when(partners.selectMentorCapacity(20L,99L)).thenReturn(Map.of("occupiedSlots",3));
            case "equal" -> when(users.selectMentorStats(10L)).thenReturn(Map.of("checkinRate",80D));
            case "closed" -> when(users.selectById(20L)).thenReturn(user(20,0));
            case "expired" -> when(users.selectMentorStats(20L)).thenReturn(Map.of("approvedDays",24,"streak",6,"checkinRate",80D));
        }
        assertThrows(IllegalArgumentException.class,()->service.acceptRequest(20L,99L));
        noRelationshipWrites();
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void onlyRecipientCanAcceptOrReject(boolean accept) {
        pending("lead");
        assertThrows(IllegalArgumentException.class,()->{if(accept)service.acceptRequest(10L,99L);else service.rejectRequest(10L,99L);});
        verify(users,never()).selectByIdForUpdate(anyLong());
        noRelationshipWrites();
    }
    @ParameterizedTest @ValueSource(strings={"accepted","rejected","blocked"})
    void processedRequestsCannotBeAcceptedOrRejectedAgain(String status) {
        when(partners.selectById(99L)).thenReturn(request(status,"lead"));
        assertThrows(IllegalArgumentException.class,()->service.acceptRequest(20L,99L));
        assertThrows(IllegalArgumentException.class,()->service.rejectRequest(20L,99L));
        noRelationshipWrites();
    }
    @Test void requestChangedWhileWaitingForUserLocksIsRejected() {
        Partner changed=request("pending","lead");changed.setUserId(30L);
        when(partners.selectById(99L)).thenReturn(request("pending","lead"),changed);
        assertThrows(IllegalArgumentException.class,()->service.acceptRequest(20L,99L));
        noRelationshipWrites();
    }
    @Test void conditionalStatusUpdateFailureDoesNotEmitSideEffects() {
        pending("mutual");when(partners.updatePendingStatus(99L,"accepted")).thenReturn(0);
        assertThrows(IllegalStateException.class,()->service.acceptRequest(20L,99L));
        verifyNoInteractions(notifications,achievements);
    }
    @Test void rejectDoesNotRequireMentorToRemainEligible() {
        pending("lead");when(users.selectById(20L)).thenReturn(user(20,0));
        when(partners.updatePendingStatus(99L,"rejected")).thenReturn(1);
        service.rejectRequest(20L,99L);
        verify(rules,never()).validateLead(anyLong(),anyLong(),any());
        verify(partners).updatePendingStatus(99L,"rejected");
    }
    @Test void resendingPrefersSameDirectionRejectedRowToAvoidUniqueKeyCollision() {
        Partner reverse=request("rejected","mutual");reverse.setId(98L);reverse.setUserId(20L);reverse.setPartnerId(10L);
        Partner same=request("rejected","mutual");
        when(partners.selectAllByUserAndPartner(10L,20L)).thenReturn(List.of(reverse,same));
        when(partners.resetRequest(any())).thenReturn(1);
        service.sendRequest(10L,20L,null,"lead");
        ArgumentCaptor<Partner> saved=ArgumentCaptor.forClass(Partner.class);
        verify(partners).resetRequest(saved.capture());
        assertEquals(99L,saved.getValue().getId());
        assertEquals(20L,reverse.getUserId());
        assertEquals(10L,reverse.getPartnerId());
        assertEquals("rejected",reverse.getStatus());
    }
    @ParameterizedTest @ValueSource(strings={"accepted","pending"})
    void unblockingLeadDoesNotRestoreReleasedCapacity(String previousStatus) {
        Partner blocked=request("blocked","lead");blocked.setPrevStatus(previousStatus);
        when(partners.selectAllByUserAndPartner(10L,20L)).thenReturn(List.of(blocked));
        service.unblockPartner(10L,20L);
        verify(partners).updateStatus(99L,"rejected");
        verify(partners).updatePrevStatus(99L,null);
        verify(partners,never()).updateStatus(99L,previousStatus);
    }
    @Test void blockingMentorReleasesReverseAcceptedLeadSlot() {
        Partner accepted=request("accepted","lead");
        when(partners.selectAllByUserAndPartner(20L,10L)).thenReturn(List.of(accepted));
        service.blockPartner(20L,10L);
        verify(partners).updateStatus(99L,"rejected");
        ArgumentCaptor<Partner> saved=ArgumentCaptor.forClass(Partner.class);
        verify(partners).insert(saved.capture());
        assertEquals("blocked",saved.getValue().getStatus());
        assertEquals(20L,saved.getValue().getUserId());
    }
    @Test void recommendationsUseActualRecentRateAndNormalizeLegacyGoalTypes() {
        User applicant=user(10,0);applicant.setLevel(99);applicant.setProvince("province");
        when(users.selectById(10L)).thenReturn(applicant);
        when(users.selectMatchCandidates(eq(10L),anyList(),eq("province"),eq("mentor"),eq(1),eq(33.3)))
                .thenReturn(List.of());
        assertTrue(service.getMatchRecommendations(10L,"study"," mentor ","lead").isEmpty());
        verify(users).selectMatchCandidates(10L,List.of("study","学习","学习提升"),"province","mentor",1,33.3);
    }
    @Test void workflowMethodsDeclareReadCommittedAndExceptionRollback() throws Exception {
        for (var method: List.of(
                PartnerServiceImpl.class.getMethod("sendRequest",Long.class,Long.class,Long.class,String.class),
                PartnerServiceImpl.class.getMethod("acceptRequest",Long.class,Long.class),
                PartnerServiceImpl.class.getMethod("rejectRequest",Long.class,Long.class))) {
            Transactional tx=method.getAnnotation(Transactional.class);
            assertNotNull(tx,method.getName());
            assertEquals(Isolation.READ_COMMITTED,tx.isolation());
            assertTrue(List.of(tx.rollbackFor()).contains(Exception.class));
        }
    }
}
