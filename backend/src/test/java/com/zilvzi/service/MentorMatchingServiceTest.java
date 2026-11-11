package com.zilvzi.service;

import com.zilvzi.mapper.PartnerMapper;
import com.zilvzi.mapper.UserMapper;
import com.zilvzi.pojo.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MentorMatchingServiceTest {
    UserMapper users;
    PartnerMapper partners;
    MentorMatchingService service;

    @BeforeEach void setup() {
        users = mock(UserMapper.class);
        partners = mock(PartnerMapper.class);
        service = new MentorMatchingService();
        ReflectionTestUtils.setField(service, "userMapper", users);
        ReflectionTestUtils.setField(service, "partnerMapper", partners);
    }

    static Map<String, Object> stats(int days, int streak) {
        return stats(days, days * 100D / 30, streak);
    }
    static Map<String, Object> stats(int days, double rate, int streak) {
        return Map.of("approvedDays", days, "checkinRate", BigDecimal.valueOf(rate), "streak", streak);
    }
    static User user(long id, int enabled) {
        User user = new User(); user.setId(id); user.setMentorEnabled(enabled); return user;
    }
    void qualifiedMentor() {
        when(users.selectById(20L)).thenReturn(user(20, 1));
        when(users.selectMentorStats(20L)).thenReturn(stats(24, 80, 7));
        when(users.selectMentorStats(10L)).thenReturn(stats(10, 33.3, 2));
    }

    @ParameterizedTest
    @CsvSource({"23,80,7,false", "24,79.9,7,false", "24,80,6,false", "24,80,7,true", "30,100,7,true", "30,100,0,false", "0,0,7,false"})
    void qualificationRequiresTwentyFourDaysEightyPercentAndSevenDayStreak(int days, double rate, int streak, boolean expected) {
        assertEquals(expected, MentorMatchingService.isEligible(stats(days, rate, streak)));
    }
    @Test void absentOrMissingRateStatisticsDoNotQualify() {
        assertFalse(MentorMatchingService.isEligible(null));
        assertFalse(MentorMatchingService.isEligible(Map.of()));
        assertFalse(MentorMatchingService.isEligible(Map.of("approvedDays", 24, "streak", 7)));
    }
    @Test void exactlyEightyPercentMentorCanLeadAnApplicantWithLowerRate() {
        qualifiedMentor();
        when(partners.selectMentorCapacity(20L, null)).thenReturn(Map.of("occupiedSlots", 0));
        assertNull(service.unavailableReason(10L, 20L, null));
    }
    @Test void mentorWithSameRateAsApplicantIsRejected() {
        qualifiedMentor();
        when(users.selectMentorStats(10L)).thenReturn(stats(24, 80, 7));
        assertTrue(service.unavailableReason(10L, 20L, null).contains("高于"));
        verifyNoInteractions(partners);
    }
    @Test void mentorBelowEligibilityRateIsRejectedBeforeComparingApplicant() {
        when(users.selectById(20L)).thenReturn(user(20, 1));
        when(users.selectMentorStats(20L)).thenReturn(stats(24, 79.9, 7));
        assertTrue(service.unavailableReason(10L, 20L, null).contains("资格"));
        verify(users, never()).selectMentorStats(10L);
        verifyNoInteractions(partners);
    }
    @Test void closedMentorCannotReceiveApplication() {
        when(users.selectById(20L)).thenReturn(user(20, 0));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> service.validateLead(10L,20L,null)).getMessage().contains("关闭"));
        verify(users, never()).selectMentorStats(anyLong());
    }
    @Test void expiredStreakCannotReceiveApplicationDespiteHighRate() {
        when(users.selectById(20L)).thenReturn(user(20, 1));
        when(users.selectMentorStats(20L)).thenReturn(stats(29, 0));
        assertTrue(service.unavailableReason(10L,20L,null).contains("资格"));
        verifyNoInteractions(partners);
    }
    @ParameterizedTest @CsvSource({"24", "25", "30"})
    void mentorMustBeStrictlyStrongerThanApplicant(int applicantDays) {
        qualifiedMentor();
        when(users.selectMentorStats(10L)).thenReturn(stats(applicantDays, 7));
        assertTrue(service.unavailableReason(10L,20L,null).contains("高于"));
        verifyNoInteractions(partners);
    }
    @ParameterizedTest @CsvSource({"0,true", "1,true", "2,true", "3,false", "4,false"})
    void pendingAndAcceptedCapacityIsEnforced(int occupied, boolean available) {
        qualifiedMentor();
        when(partners.selectMentorCapacity(20L,null)).thenReturn(Map.of("occupiedSlots",occupied));
        assertEquals(available, service.unavailableReason(10L,20L,null) == null);
    }
    @Test void acceptingExcludesTheCurrentReservedPendingSlot() {
        qualifiedMentor();
        when(partners.selectMentorCapacity(20L,99L)).thenReturn(Map.of("occupiedSlots",2));
        assertDoesNotThrow(() -> service.validateLead(10L,20L,99L));
        verify(partners).selectMentorCapacity(20L,99L);
        verify(partners,never()).selectMentorCapacity(20L,null);
    }
    @Test void missingOrSelfMentorIsRejected() {
        assertNotNull(service.unavailableReason(10L,10L,null));
        assertNotNull(service.unavailableReason(10L,20L,null));
    }
    @Test void statusReportsCapacityAndClampsRemainingToZero() {
        when(users.selectById(20L)).thenReturn(user(20,1));
        when(users.selectMentorStats(20L)).thenReturn(stats(24,7));
        when(partners.selectMentorCapacity(20L,null)).thenReturn(Map.of("occupiedSlots",4,"pendingCount",2,"acceptedCount",2));
        Map<String,Object> status=service.getStatus(20L);
        assertEquals(true,status.get("eligible"));
        assertEquals(true,status.get("enabled"));
        assertEquals(0,status.get("remainingSlots"));
        assertEquals(2,status.get("pendingCount"));
        assertEquals(24,status.get("requiredApprovedDays"));
        assertEquals(7,status.get("requiredDays"));
    }
    @ParameterizedTest
    @CsvSource({"0,24,80,7,0,disabled", "1,24,80,0,1,ineligible", "1,24,80,7,3,full", "1,24,80,7,1,available", "1,23,76.7,0,3,ineligible"})
    void statusSeparatesOptInFromRecommendation(int enabled, int days, double rate, int streak, int occupied, String expected) {
        when(users.selectById(20L)).thenReturn(user(20, enabled));
        when(users.selectMentorStats(20L)).thenReturn(stats(days, rate, streak));
        when(partners.selectMentorCapacity(20L, null)).thenReturn(Map.of("occupiedSlots", occupied));
        Map<String, Object> status = service.getStatus(20L);
        assertEquals(expected, status.get("recommendationState"));
        assertEquals("available".equals(expected), status.get("recommendationAvailable"));
        assertEquals(enabled == 1, status.get("enabled"));
        java.util.List<?> unmet = (java.util.List<?>) status.get("unmetRequirements");
        assertEquals((days < 24 ? 1 : 0) + (rate < 80 ? 1 : 0) + (streak < 7 ? 1 : 0), unmet.size());
        if (streak < 7) assertTrue(unmet.contains("当前连续有效打卡不足7天"));
    }

    @Test void recommendationResumesWhenQualificationRecoversWithoutChangingOptIn() {
        when(users.selectById(20L)).thenReturn(user(20, 1));
        when(users.selectMentorStats(20L)).thenReturn(stats(24, 80, 0), stats(24, 80, 7));
        when(partners.selectMentorCapacity(20L, null)).thenReturn(Map.of("occupiedSlots", 1));
        assertEquals("ineligible", service.getStatus(20L).get("recommendationState"));
        assertEquals("available", service.getStatus(20L).get("recommendationState"));
    }

    @ParameterizedTest @CsvSource({"20,10", "10,20"})
    void locksUsersInAscendingIdOrderRegardlessOfDirection(long first,long second) {
        when(users.selectByIdForUpdate(10L)).thenReturn(user(10,0));
        when(users.selectByIdForUpdate(20L)).thenReturn(user(20,1));
        service.lockUsers(first,second);
        InOrder order=inOrder(users);
        order.verify(users).selectByIdForUpdate(10L);
        order.verify(users).selectByIdForUpdate(20L);
    }
    @Test void invalidLockSubjectsFailWithoutAcquiringLocks() {
        assertThrows(IllegalArgumentException.class,()->service.lockUsers(null,20L));
        assertThrows(IllegalArgumentException.class,()->service.lockUsers(20L,20L));
        verifyNoInteractions(users);
    }
    @Test void unsupportedModeIsRejectedAndLegacyAbsentModeDefaultsToMutual() {
        assertEquals("mutual", MentorMatchingService.normalizeMode(null));
        assertEquals("mutual", MentorMatchingService.normalizeMode(" "));
        assertThrows(IllegalArgumentException.class,()->MentorMatchingService.normalizeMode("vip"));
    }
}
