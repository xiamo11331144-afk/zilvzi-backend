package com.zilvzi.service.impl;

import com.zilvzi.mapper.UserMapper;
import com.zilvzi.pojo.User;
import com.zilvzi.service.MentorMatchingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserMentorSettingsTest {
    UserMapper users;
    MentorMatchingService rules;
    UserServiceImpl service;
    @BeforeEach void setup() {
        users=mock(UserMapper.class);rules=mock(MentorMatchingService.class);
        service=new UserServiceImpl();
        ReflectionTestUtils.setField(service,"userMapper",users);
        ReflectionTestUtils.setField(service,"mentorMatchingService",rules);
    }
    @Test void enablingLocksUserBeforeCheckingQualificationAndWriting() {
        when(users.selectByIdForUpdate(11L)).thenReturn(new User());
        when(rules.getStatus(11L)).thenReturn(Map.of("eligible",true));
        service.setMentorEnabled(11L,true);
        InOrder order=inOrder(users,rules);
        order.verify(users).selectByIdForUpdate(11L);
        order.verify(rules).getStatus(11L);
        order.verify(users).updateMentorEnabled(11L,1);
    }
    @Test void ineligibleAccountCannotEnable() {
        when(users.selectByIdForUpdate(11L)).thenReturn(new User());
        when(rules.getStatus(11L)).thenReturn(Map.of("eligible",false));
        assertThrows(IllegalArgumentException.class,()->service.setMentorEnabled(11L,true));
        verify(users,never()).updateMentorEnabled(anyLong(),anyInt());
    }
    @Test void disablingIsAlwaysAllowedAfterQualificationExpires() {
        when(users.selectByIdForUpdate(11L)).thenReturn(new User());
        service.setMentorEnabled(11L,false);
        verify(users).updateMentorEnabled(11L,0);
        verifyNoInteractions(rules);
    }
    @Test void nonexistentAccountCannotChangeSetting() {
        assertThrows(IllegalArgumentException.class,()->service.setMentorEnabled(11L,false));
        verify(users,never()).updateMentorEnabled(anyLong(),anyInt());
    }
}
