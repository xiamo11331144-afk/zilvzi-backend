package com.zilvzi.controller;

import com.zilvzi.mapper.NotificationMapper;
import com.zilvzi.pojo.Notification;
import com.zilvzi.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationControllerReadTest {
    @Test void ownerCanReadNotificationAndOtherUserCannot() {
        NotificationMapper mapper = mock(NotificationMapper.class);
        NotificationService service = mock(NotificationService.class);
        NotificationController controller = new NotificationController();
        ReflectionTestUtils.setField(controller, "notificationMapper", mapper);
        ReflectionTestUtils.setField(controller, "notificationService", service);
        Notification notification = new Notification(); notification.setId(360L); notification.setUserId(1L);
        when(mapper.selectById(360L)).thenReturn(notification);
        assertEquals(403, controller.markAsRead(2L, 360L).getCode());
        verifyNoInteractions(service);
        assertEquals(200, controller.markAsRead(1L, 360L).getCode());
        verify(service).markAsRead(360L);
    }

    @Test void missingNotificationIsNotMarkedRead() {
        NotificationMapper mapper = mock(NotificationMapper.class);
        NotificationService service = mock(NotificationService.class);
        NotificationController controller = new NotificationController();
        ReflectionTestUtils.setField(controller, "notificationMapper", mapper);
        ReflectionTestUtils.setField(controller, "notificationService", service);
        assertEquals(403, controller.markAsRead(1L, 360L).getCode());
        verifyNoInteractions(service);
    }
}
