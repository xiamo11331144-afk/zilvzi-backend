package com.zilvzi.service.impl;

import com.zilvzi.exception.BusinessException;
import com.zilvzi.mapper.UserMapper;
import com.zilvzi.mapper.VipMapper;
import com.zilvzi.pojo.VipOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VipServiceImplTest {
    VipMapper vipMapper;
    UserMapper users;
    Environment environment;
    VipServiceImpl service;

    @BeforeEach void setup() {
        vipMapper = mock(VipMapper.class);
        users = mock(UserMapper.class);
        environment = mock(Environment.class);
        service = new VipServiceImpl(vipMapper, users, environment);
        ReflectionTestUtils.setField(service, "durationDays", 30);
        ReflectionTestUtils.setField(service, "priceFen", 990);
        ReflectionTestUtils.setField(service, "paymentChannel", "mock");
        ReflectionTestUtils.setField(service, "mockEnabled", true);
    }

    @Test void membershipExtensionUsesFreshReadAfterUserLock() throws Exception {
        var transaction = VipServiceImpl.class.getMethod("mockConfirm", Long.class, String.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class);
        assertNotNull(transaction);
        assertEquals(org.springframework.transaction.annotation.Isolation.READ_COMMITTED, transaction.isolation());
    }

    @Test void membershipExpiresAtExactBoundaryAndMissingExpiryIsFree() {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
        assertTrue(VipServiceImpl.activeAt(now.plusNanos(1), now));
        assertFalse(VipServiceImpl.activeAt(now, now));
        assertFalse(VipServiceImpl.activeAt(now.minusNanos(1), now));
        assertFalse(VipServiceImpl.activeAt(null, now));
    }

    @Test void mockPaymentRequiresMockChannelFlagAndOnlyDevOrTestProfiles() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{});
        assertEquals("disabled", service.plan().get("paymentMode"));
        when(environment.getActiveProfiles()).thenReturn(new String[]{"test"});
        assertEquals("mock", service.plan().get("paymentMode"));
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        assertEquals("mock", service.plan().get("paymentMode"));
        for (String profile : new String[]{"prod", "production"}) {
            when(environment.getActiveProfiles()).thenReturn(new String[]{profile});
            assertEquals("disabled", service.plan().get("paymentMode"));
        }
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev", "prod"});
        assertEquals("disabled", service.plan().get("paymentMode"));
        when(environment.getActiveProfiles()).thenReturn(new String[]{"test", "production"});
        assertEquals("disabled", service.plan().get("paymentMode"));
        when(environment.getActiveProfiles()).thenReturn(new String[]{"test", "staging"});
        assertEquals("disabled", service.plan().get("paymentMode"));
        ReflectionTestUtils.setField(service, "mockEnabled", false);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"test"});
        assertEquals("disabled", service.plan().get("paymentMode"));
        ReflectionTestUtils.setField(service, "mockEnabled", true);
        ReflectionTestUtils.setField(service, "paymentChannel", "disabled");
        assertEquals("disabled", service.plan().get("paymentMode"));
    }

    @Test void nonOwnerCannotReadOrConfirmOrder() {
        VipOrder order = order(44L, "PENDING");
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        when(vipMapper.selectOrder("abc")).thenReturn(order);
        assertThrows(BusinessException.class, () -> service.getOrder(45L, "abc"));
        assertThrows(BusinessException.class, () -> service.mockConfirm(45L, "abc"));
        verify(vipMapper, never()).confirmOrder(anyString());
        verify(vipMapper, never()).upsertVip(anyLong(), any());
    }

    @Test void confirmingSameOrderTwiceExtendsMembershipOnlyOnce() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        VipOrder pending = order(44L, "PENDING");
        VipOrder confirmed = order(44L, "TEST_CONFIRMED");
        when(vipMapper.selectOrder("abc")).thenReturn(pending, confirmed);
        when(users.selectByIdForUpdate(44L)).thenReturn(new com.zilvzi.pojo.User());
        when(vipMapper.confirmOrder("abc")).thenReturn(1);
        when(vipMapper.selectVipUntil(44L)).thenReturn(null);
        when(vipMapper.upsertVip(eq(44L), any())).thenReturn(1);
        assertEquals("TEST_CONFIRMED", service.mockConfirm(44L, "abc").get("status"));
        assertEquals("TEST_CONFIRMED", service.mockConfirm(44L, "abc").get("status"));
        verify(vipMapper, times(1)).confirmOrder("abc");
        verify(vipMapper, times(1)).upsertVip(eq(44L), any());
        InOrder readAfterLock = inOrder(users, vipMapper);
        readAfterLock.verify(users).selectByIdForUpdate(44L);
        readAfterLock.verify(vipMapper).confirmOrder("abc");
        readAfterLock.verify(vipMapper).selectVipUntil(44L);
        readAfterLock.verify(vipMapper).upsertVip(eq(44L), any());
    }

    @Test void statusSerializesActiveAndExpiredExpiryWithUtcOffset() {
        LocalDateTime future = LocalDateTime.now(java.time.ZoneOffset.UTC).plusDays(1);
        LocalDateTime past = LocalDateTime.now(java.time.ZoneOffset.UTC).minusDays(1);
        when(vipMapper.selectVipUntil(44L)).thenReturn(future, past, null);
        Map<String, Object> active = service.status(44L);
        assertEquals(true, active.get("isVip"));
        assertEquals(java.time.ZoneOffset.UTC, java.time.OffsetDateTime.parse((String) active.get("vipUntil")).getOffset());
        Map<String, Object> expired = service.status(44L);
        assertEquals(false, expired.get("isVip"));
        assertNotNull(expired.get("vipUntil"));
        Map<String, Object> free = service.status(44L);
        assertEquals(false, free.get("isVip"));
        assertNull(free.get("vipUntil"));
    }

    @Test void renewalAddsDurationToAnUnexpiredEndDate() {
        LocalDateTime currentExpiry = LocalDateTime.now(java.time.ZoneOffset.UTC).plusDays(10);
        LocalDateTime renewed = confirmAndCaptureExpiry(currentExpiry);
        assertEquals(currentExpiry.plusDays(30), renewed);
    }

    @Test void expiredMembershipRenewsFromConfirmationTime() {
        LocalDateTime currentExpiry = LocalDateTime.now(java.time.ZoneOffset.UTC).minusDays(1);
        LocalDateTime before = LocalDateTime.now(java.time.ZoneOffset.UTC);
        LocalDateTime renewed = confirmAndCaptureExpiry(currentExpiry);
        assertFalse(renewed.isBefore(before.plusDays(30)));
        assertTrue(renewed.isBefore(LocalDateTime.now(java.time.ZoneOffset.UTC).plusDays(30).plusSeconds(1)));
    }

    @Test void twoOrdersConfirmedSequentiallyAccumulateDuration() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"test"});
        VipOrder first = order(44L, "PENDING");
        first.setOrderNo("first");
        VipOrder firstConfirmed = order(44L, "TEST_CONFIRMED");
        firstConfirmed.setOrderNo("first");
        VipOrder second = order(44L, "PENDING");
        second.setOrderNo("second");
        VipOrder secondConfirmed = order(44L, "TEST_CONFIRMED");
        secondConfirmed.setOrderNo("second");
        when(vipMapper.selectOrder("first")).thenReturn(first, firstConfirmed);
        when(vipMapper.selectOrder("second")).thenReturn(second, secondConfirmed);
        when(users.selectByIdForUpdate(44L)).thenReturn(new com.zilvzi.pojo.User());
        when(vipMapper.confirmOrder("first")).thenReturn(1);
        when(vipMapper.confirmOrder("second")).thenReturn(1);
        java.util.concurrent.atomic.AtomicReference<LocalDateTime> storedExpiry = new java.util.concurrent.atomic.AtomicReference<>();
        when(vipMapper.selectVipUntil(44L)).thenAnswer(call -> storedExpiry.get());
        when(vipMapper.upsertVip(eq(44L), any())).thenAnswer(call -> {
            storedExpiry.set(call.getArgument(1));
            return 1;
        });
        service.mockConfirm(44L, "first");
        LocalDateTime firstExpiry = storedExpiry.get();
        service.mockConfirm(44L, "second");
        assertEquals(firstExpiry.plusDays(30), storedExpiry.get());
    }

    @Test void concurrentConfirmationLostUpdateDoesNotExtendMembership() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        VipOrder pending = order(44L, "PENDING");
        VipOrder confirmed = order(44L, "TEST_CONFIRMED");
        when(vipMapper.selectOrder("abc")).thenReturn(pending, confirmed);
        when(users.selectByIdForUpdate(44L)).thenReturn(new com.zilvzi.pojo.User());
        when(vipMapper.confirmOrder("abc")).thenReturn(0);
        assertEquals("TEST_CONFIRMED", service.mockConfirm(44L, "abc").get("status"));
        verify(vipMapper, never()).selectVipUntil(anyLong());
        verify(vipMapper, never()).upsertVip(anyLong(), any());
    }

    @Test void serviceTransactionsRollBackAllCheckedExceptions() throws Exception {
        for (String methodName : new String[]{"createOrder", "mockConfirm"}) {
            var method = java.util.Arrays.stream(VipServiceImpl.class.getMethods())
                    .filter(candidate -> candidate.getName().equals(methodName)).findFirst().orElseThrow();
            var transaction = method.getAnnotation(org.springframework.transaction.annotation.Transactional.class);
            assertNotNull(transaction);
            assertTrue(java.util.List.of(transaction.rollbackFor()).contains(Exception.class));
        }
    }

    private LocalDateTime confirmAndCaptureExpiry(LocalDateTime currentExpiry) {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        VipOrder pending = order(44L, "PENDING");
        VipOrder confirmed = order(44L, "TEST_CONFIRMED");
        when(vipMapper.selectOrder("abc")).thenReturn(pending, confirmed);
        when(users.selectByIdForUpdate(44L)).thenReturn(new com.zilvzi.pojo.User());
        when(vipMapper.confirmOrder("abc")).thenReturn(1);
        when(vipMapper.selectVipUntil(44L)).thenReturn(currentExpiry);
        when(vipMapper.upsertVip(eq(44L), any())).thenReturn(1);
        service.mockConfirm(44L, "abc");
        org.mockito.ArgumentCaptor<LocalDateTime> expiry = org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
        verify(vipMapper).upsertVip(eq(44L), expiry.capture());
        return expiry.getValue();
    }

    @Test void clientCannotChoosePriceDurationOrChannelForOrder() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        when(vipMapper.insertOrder(any())).thenAnswer(call -> {
            VipOrder order = call.getArgument(0);
            assertEquals(990, order.getPriceFen());
            assertEquals(30, order.getDurationDays());
            assertEquals("mock", order.getPaymentChannel());
            return 1;
        });
        Map<String, Object> result = service.createOrder(44L);
        assertEquals(990, result.get("priceFen"));
        assertEquals(30, result.get("durationDays"));
        assertEquals("mock", result.get("paymentMode"));
    }

    @Test void disabledPaymentRejectsOrderWithoutMapperWrites() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{});
        assertThrows(BusinessException.class, () -> service.createOrder(44L));
        verifyNoInteractions(vipMapper, users);
    }

    @Test void disabledMockConfirmationRejectsWithoutMapperReadsOrWrites() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"production"});
        assertThrows(BusinessException.class, () -> service.mockConfirm(44L, "abc"));
        verifyNoInteractions(vipMapper, users);
    }

    @Test void nonMockOrderCannotBeMockConfirmed() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        VipOrder nonMock = order(44L, "PENDING");
        nonMock.setPaymentChannel("wechat");
        when(vipMapper.selectOrder("abc")).thenReturn(nonMock);
        assertThrows(BusinessException.class, () -> service.mockConfirm(44L, "abc"));
        verify(users, never()).selectByIdForUpdate(anyLong());
        verify(vipMapper, never()).confirmOrder(anyString());
        verify(vipMapper, never()).upsertVip(anyLong(), any());
    }

    private VipOrder order(long userId, String status) {
        VipOrder order = new VipOrder();
        order.setOrderNo("abc");
        order.setUserId(userId);
        order.setPriceFen(990);
        order.setDurationDays(30);
        order.setPaymentChannel("mock");
        order.setStatus(status);
        return order;
    }
}
