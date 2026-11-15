package com.zilvzi.controller;

import com.zilvzi.pojo.Post;
import com.zilvzi.service.CircleService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CircleNotificationNavigationTest {
    @Test void existingPostExposesItsCircleForNotificationNavigation() {
        CircleService service = mock(CircleService.class);
        CircleController controller = new CircleController();
        ReflectionTestUtils.setField(controller, "circleService", service);
        Post post = new Post(); post.setId(9L); post.setCircleId(3L);
        when(service.getPostById(9L)).thenReturn(post);
        var result = controller.getPost(9L);
        assertEquals(200, result.getCode());
        assertSame(post, result.getData());
    }
    @Test void deletedPostReturnsBusinessFailureInsteadOfEmptySuccess() {
        CircleService service = mock(CircleService.class);
        CircleController controller = new CircleController();
        ReflectionTestUtils.setField(controller, "circleService", service);
        assertNotEquals(200, controller.getPost(9L).getCode());
    }
}
