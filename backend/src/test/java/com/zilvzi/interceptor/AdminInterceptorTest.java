package com.zilvzi.interceptor;

import com.zilvzi.mapper.UserMapper;
import com.zilvzi.pojo.User;
import com.zilvzi.utils.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class AdminInterceptorTest {
    private AdminInterceptor interceptor;
    private JwtUtil jwtUtil;
    private UserMapper userMapper;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        interceptor = new AdminInterceptor();
        jwtUtil = mock(JwtUtil.class);
        userMapper = mock(UserMapper.class);
        ReflectionTestUtils.setField(interceptor, "jwtUtil", jwtUtil);
        ReflectionTestUtils.setField(interceptor, "userMapper", userMapper);
        request = new MockHttpServletRequest("GET", "/api/admin/stats");
        request.addHeader("Authorization", "Bearer admin-token");
        response = new MockHttpServletResponse();
        when(jwtUtil.validateToken("admin-token")).thenReturn(true);
        when(jwtUtil.isAdminToken("admin-token")).thenReturn(true);
        when(jwtUtil.getUserId("admin-token")).thenReturn(7L);
    }

    @Test
    void allowsOnlyExistingCurrentAdministrator() throws Exception {
        User admin = new User();
        admin.setId(7L);
        admin.setRole(1);
        when(userMapper.selectById(7L)).thenReturn(admin);

        assertTrue(interceptor.preHandle(request, response, new Object()));
        verify(userMapper).selectById(7L);
    }

    @Test
    void rejectsTokenAfterAccountDeletion() throws Exception {
        when(userMapper.selectById(7L)).thenReturn(null);

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus());
    }

    @Test
    void rejectsTokenAfterAdministratorDemotion() throws Exception {
        User demoted = new User();
        demoted.setId(7L);
        demoted.setRole(0);
        when(userMapper.selectById(7L)).thenReturn(demoted);

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(403, response.getStatus());
    }

    @Test
    void rejectsInvalidJwtBeforeReadingAccount() throws Exception {
        when(jwtUtil.validateToken("admin-token")).thenReturn(false);

        assertFalse(interceptor.preHandle(request, response, new Object()));
        verifyNoInteractions(userMapper);
    }
}
