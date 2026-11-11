package com.zilvzi.service.impl;

import com.zilvzi.mapper.TopicMapper;
import com.zilvzi.utils.SensitiveWordFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TopicServiceImplTest {
    private TopicMapper topics;
    private SensitiveWordFilter filter;
    private TopicServiceImpl service;

    @BeforeEach void setUp() {
        topics = mock(TopicMapper.class);
        filter = new SensitiveWordFilter();
        service = new TopicServiceImpl();
        ReflectionTestUtils.setField(service, "topicMapper", topics);
        ReflectionTestUtils.setField(service, "sensitiveWordFilter", filter);
    }

    @Test void updateRejectsSensitiveTitleAndContent() {
        assertThrows(IllegalArgumentException.class, () -> service.updateTopic(7L, Map.of("title", "赌博资讯")));
        assertThrows(IllegalArgumentException.class, () -> service.updateTopic(7L, Map.of("content", "普通内容，包含色情")));
        verifyNoInteractions(topics);
    }

    @Test void updateValidatesFieldsAndPreservesOwnerPinFieldBoundary() {
        assertThrows(IllegalArgumentException.class, () -> service.updateTopic(7L, Map.of("userId", 1)));
        assertThrows(IllegalArgumentException.class, () -> service.updateTopic(7L, Map.of("title", " ")));
        assertThrows(IllegalArgumentException.class, () -> service.updateTopic(7L, Map.of("title", "x".repeat(201))));
        assertThrows(IllegalArgumentException.class, () -> service.updateTopic(7L, Map.of("isPinned", 2)));
        verifyNoInteractions(topics);

        service.updateTopic(7L, Map.of("isPinned", 1));
        verify(topics).update(7L, null, null, 1);
    }

    @Test void updateTrimsValidTitleAndAcceptsPartialContent() {
        service.updateTopic(7L, Map.of("title", "  标题  "));
        verify(topics).update(7L, "标题", null, null);
    }
}
