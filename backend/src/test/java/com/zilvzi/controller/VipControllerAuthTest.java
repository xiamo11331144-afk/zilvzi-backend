package com.zilvzi.controller;

import com.zilvzi.mapper.UserMapper;
import com.zilvzi.mapper.VipMapper;
import com.zilvzi.pojo.VipOrder;
import com.zilvzi.service.impl.VipServiceImpl;
import com.zilvzi.interceptor.LoginInterceptor;
import com.zilvzi.utils.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class VipControllerAuthTest {
    private VipMapper vipMapper;
    private UserMapper users;
    private MockMvc mvc;
    private JwtUtil jwt;

    @BeforeEach void setup() {
        vipMapper = mock(VipMapper.class);
        users = mock(UserMapper.class);
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"test"});
        VipServiceImpl service = new VipServiceImpl(vipMapper, users, environment);
        ReflectionTestUtils.setField(service, "durationDays", 30);
        ReflectionTestUtils.setField(service, "priceFen", 990);
        ReflectionTestUtils.setField(service, "paymentChannel", "mock");
        ReflectionTestUtils.setField(service, "mockEnabled", true);

        jwt = new JwtUtil();
        ReflectionTestUtils.setField(jwt, "secret", "unit-test-signing-key-must-be-32bytes-long");
        ReflectionTestUtils.setField(jwt, "expiration", 60_000L);
        LoginInterceptor login = new LoginInterceptor();
        ReflectionTestUtils.setField(login, "jwtUtil", jwt);
        mvc = MockMvcBuilders.standaloneSetup(new VipController(service)).addInterceptors(login).build();
    }

    @Test void missingBearerTokenIsRejectedBeforeVipServiceOrMapper() throws Exception {
        mvc.perform(post("/api/vip/orders")).andExpect(status().isUnauthorized());
        verifyNoInteractions(vipMapper, users);
    }

    @Test void authenticatedIdentityWinsAndClientCannotForgeOrderTerms() throws Exception {
        when(vipMapper.insertOrder(any(VipOrder.class))).thenReturn(1);
        String token = jwt.generateToken(44L, "authenticated");
        mvc.perform(post("/api/vip/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"userId\":99,\"priceFen\":1,\"durationDays\":999,\"paymentChannel\":\"wechat\",\"isVip\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.priceFen").value(990))
                .andExpect(jsonPath("$.data.durationDays").value(30))
                .andExpect(jsonPath("$.data.paymentMode").value("mock"));
        org.mockito.ArgumentCaptor<VipOrder> order = org.mockito.ArgumentCaptor.forClass(VipOrder.class);
        verify(vipMapper).insertOrder(order.capture());
        assertEquals(44L, order.getValue().getUserId());
        assertEquals(990, order.getValue().getPriceFen());
        assertEquals(30, order.getValue().getDurationDays());
        assertEquals("mock", order.getValue().getPaymentChannel());
    }

    @Test void statusUsesAuthenticatedUserIdAndSerializesOffsetDateTime() throws Exception {
        java.time.LocalDateTime expiry = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).plusDays(2);
        when(vipMapper.selectVipUntil(44L)).thenReturn(expiry);
        mvc.perform(get("/api/vip/status").header("Authorization", "Bearer " + jwt.generateToken(44L, "user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isVip").value(true))
                .andExpect(jsonPath("$.data.vipUntil").value(org.hamcrest.Matchers.endsWith("Z")));
        verify(vipMapper).selectVipUntil(44L);
        verify(vipMapper, never()).selectVipUntil(99L);
    }
}
