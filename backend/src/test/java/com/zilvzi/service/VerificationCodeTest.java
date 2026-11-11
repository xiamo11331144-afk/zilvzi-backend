package com.zilvzi.service;

import com.zilvzi.mapper.UserMapper;
import com.zilvzi.service.impl.UserServiceImpl;
import com.zilvzi.utils.RedisUtil;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VerificationCodeTest {
    final VerificationCodeSender sender=mock(VerificationCodeSender.class);
    final RedisUtil redis=mock(RedisUtil.class);
    final UserServiceImpl service=new UserServiceImpl();
    VerificationCodeTest() {
        ReflectionTestUtils.setField(service,"verificationCodeSender",sender);
        ReflectionTestUtils.setField(service,"redisUtil",redis);
        ReflectionTestUtils.setField(service,"userMapper",mock(UserMapper.class));
    }
    @Test void cachesSixDigitCodeOnlyAfterDeliveryAcknowledged() {
        service.sendVerifyCode("13800000000","phone");
        ArgumentCaptor<String> code=ArgumentCaptor.forClass(String.class);
        var order=inOrder(sender,redis);
        order.verify(sender).send(eq("13800000000"),eq("phone"),code.capture());
        assertTrue(code.getValue().matches("[1-9][0-9]{5}"));
        order.verify(redis).setex("verify_code:13800000000",code.getValue(),300);
    }
    @Test void failedDeliveryDoesNotActivateCode() {
        doThrow(new IllegalStateException("验证码发送失败，请稍后重试")).when(sender).send(anyString(),anyString(),anyString());
        assertThrows(IllegalStateException.class,()->service.sendVerifyCode("reader@example.com","email"));
        verifyNoInteractions(redis);
    }
    @Test void invalidDestinationOrTypeNeverCallsProviderOrRedis() {
        for(String[] args:new String[][]{{"bad","phone"},{"bad","email"},{"13800000000","other"},{null,"phone"}})
            assertThrows(IllegalArgumentException.class,()->service.sendVerifyCode(args[0],args[1]));
        verifyNoInteractions(sender,redis);
    }
}
