package com.zilvzi.service;

import com.zilvzi.mapper.MessageMapper;
import com.zilvzi.mapper.NotificationMapper;
import com.zilvzi.mapper.PartnerMapper;
import com.zilvzi.pojo.Notification;
import com.zilvzi.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
    @Mock NotificationMapper notificationMapper;
    @Mock MessageMapper messageMapper;
    @Mock PartnerMapper partnerMapper;
    @InjectMocks NotificationServiceImpl service;

    @Test void categoryPaginationFetchesOneExtraAndReturnsCategoryUnreadCount() {
        List<Notification> rows = List.of(new Notification(), new Notification(), new Notification());
        when(notificationMapper.selectByUserIdAndCategory(42L, "circle", 4, 3)).thenReturn(rows);
        when(notificationMapper.countUnreadByCategory(42L, "circle")).thenReturn(4);

        Map<String, Object> result = service.getUserNotifications(42L, 3, 2, "circle");

        assertEquals(2, ((List<?>) result.get("list")).size());
        assertEquals(true, result.get("hasMore"));
        assertEquals(4, result.get("unreadCount"));
        verify(notificationMapper).selectByUserIdAndCategory(42L, "circle", 4, 3);
    }

    @Test void categoryPaginationClampsOverflowingOffsetToIntegerMaximum() {
        when(notificationMapper.selectByUserIdAndCategory(42L, "notice", Integer.MAX_VALUE, 101))
                .thenReturn(List.of());
        when(notificationMapper.countUnreadByCategory(42L, "notice")).thenReturn(0);

        Map<String, Object> result = service.getUserNotifications(42L, Integer.MAX_VALUE, 100, "notice");

        assertEquals(false, result.get("hasMore"));
        verify(notificationMapper).selectByUserIdAndCategory(42L, "notice", Integer.MAX_VALUE, 101);
    }

    @Test void invalidCategoryFailsBeforeAnyMapperOperation() {
        assertThrows(IllegalArgumentException.class, () -> service.getUserNotifications(42L, 1, 20, "unknown"));
        assertThrows(IllegalArgumentException.class, () -> service.markAllAsReadByCategory(42L, "unknown"));
        assertThrows(IllegalArgumentException.class, () -> service.clearAllByCategory(42L, "unknown"));
        verifyNoInteractions(notificationMapper, messageMapper, partnerMapper);
    }

    @Test void categoryReadAndClearAreScopedToAuthenticatedUserAndCategory() {
        service.markAllAsReadByCategory(42L, "notice");
        service.clearAllByCategory(42L, "circle");
        verify(notificationMapper).markAllAsReadByCategory(42L, "notice");
        verify(notificationMapper).deleteByCategory(42L, "circle");
    }

    @Test void badgesUseNotificationBucketsRealMessagesAndReceivedPendingRequests() {
        Map<String, Object> notificationCounts = new HashMap<>();
        notificationCounts.put("notificationUnread", 5L);
        notificationCounts.put("circleUnread", 3L);
        when(notificationMapper.selectUnreadBadgeCounts(42L)).thenReturn(notificationCounts);
        when(messageMapper.countUnread(42L)).thenReturn(7);
        when(partnerMapper.countPendingReceivedRequests(42L)).thenReturn(2);

        Map<String, Object> badges = service.getBadges(42L);

        assertEquals(Map.of("notificationUnread", 5, "circleUnread", 3, "privateUnread", 7, "pendingRequests", 2), badges);
        verify(messageMapper, times(1)).countUnread(42L);
        verify(partnerMapper).countPendingReceivedRequests(42L);
    }

    @Test void badgeCountsNormalizeNullAndNegativeValuesToZero() {
        when(notificationMapper.selectUnreadBadgeCounts(42L)).thenReturn(Map.of("notificationUnread", -1, "circleUnread", 2));
        when(messageMapper.countUnread(42L)).thenReturn(null);
        when(partnerMapper.countPendingReceivedRequests(42L)).thenReturn(0);

        assertEquals(Map.of("notificationUnread", 0, "circleUnread", 2, "privateUnread", 0, "pendingRequests", 0), service.getBadges(42L));
    }
}
