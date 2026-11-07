package com.zilvzi.config;

import com.zilvzi.mapper.UserMapper;
import com.zilvzi.pojo.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminInitializerTest {
    private UserMapper userMapper;
    private AdminInitializer initializer;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        initializer = new AdminInitializer();
        ReflectionTestUtils.setField(initializer, "userMapper", userMapper);
        ReflectionTestUtils.setField(initializer, "adminUsername", "admin");
    }

    @Test
    void missingBootstrapPasswordSkipsWithoutDatabaseInteraction() {
        ReflectionTestUtils.setField(initializer, "adminPassword", " ");

        initializer.run();

        verifyNoInteractions(userMapper);
    }

    @Test
    void createsAdministratorWithBcryptEncodedPassword() {
        ReflectionTestUtils.setField(initializer, "adminPassword", "configured-test-password");
        when(userMapper.selectByUsername("admin")).thenReturn(null);

        initializer.run();

        var captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        User created = captor.getValue();
        assertEquals("admin", created.getUsername());
        assertEquals(1, created.getRole());
        assertNotEquals("configured-test-password", created.getPassword());
        assertTrue(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                .matches("configured-test-password", created.getPassword()));
        verify(userMapper, never()).updateAdmin(any(User.class));
    }

    @Test
    void sameNameRegularUserFailsWithoutPromotingAccount() {
        ReflectionTestUtils.setField(initializer, "adminPassword", "configured-test-password");
        User existing = new User();
        existing.setRole(0);
        when(userMapper.selectByUsername("admin")).thenReturn(existing);

        IllegalStateException error = assertThrows(IllegalStateException.class, initializer::run);

        assertTrue(error.getMessage().contains("no role changes were made"));
        verify(userMapper, never()).updateAdmin(any(User.class));
        verify(userMapper, never()).insert(any(User.class));
    }
}
