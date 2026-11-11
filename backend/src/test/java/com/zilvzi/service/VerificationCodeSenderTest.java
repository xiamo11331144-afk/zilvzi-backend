package com.zilvzi.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VerificationCodeSenderTest {
    final HttpClient http=mock(HttpClient.class);
    final VerificationCodeSender sender=new VerificationCodeSender();
    VerificationCodeSenderTest() {
        ReflectionTestUtils.setField(sender,"httpClient",http);
        ReflectionTestUtils.setField(sender,"url","https://delivery.example.test/code");
        ReflectionTestUtils.setField(sender,"token","test-bridge-token");
    }
    @SuppressWarnings("unchecked")
    HttpResponse<String> response(int status,String body) throws Exception {
        HttpResponse<String> response=mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status); when(response.body()).thenReturn(body);
        when(http.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(response);
        return response;
    }
    @Test void missingConfigurationFailsWithoutSending() {
        ReflectionTestUtils.setField(sender,"url","");
        assertTrue(assertThrows(IllegalStateException.class,()->sender.send("reader@example.com","email","123456"))
                .getMessage().contains("未配置"));
        verifyNoInteractions(http);
    }
    @Test void requiresHttpsAndDoesNotExposeConfiguredSecrets() {
        ReflectionTestUtils.setField(sender,"url","http://delivery.example.test/code?secret=private");
        var error=assertThrows(IllegalStateException.class,()->sender.send("reader@example.com","email","123456"));
        assertEquals("验证码发送失败，请稍后重试",error.getMessage()); assertNull(error.getCause());
        verifyNoInteractions(http);
    }
    @Test void acceptsOnlySuccessfulExplicitProviderAcknowledgement() throws Exception {
        response(200,"{\"accepted\":true}");
        assertDoesNotThrow(()->sender.send("reader@example.com","email","123456"));
        for(String body:new String[]{"{}","{\"accepted\":false}","{\"accepted\":\"true\"}","invalid"}) {
            response(200,body);
            assertThrows(IllegalStateException.class,()->sender.send("reader@example.com","email","123456"));
        }
        response(503,"{\"accepted\":true}");
        assertThrows(IllegalStateException.class,()->sender.send("reader@example.com","email","123456"));
    }
    @SuppressWarnings("unchecked")
    @Test void networkFailuresAndInterruptsStayGeneric() throws Exception {
        when(http.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class)))
                .thenThrow(new java.io.IOException("private-provider-secret"));
        var error=assertThrows(IllegalStateException.class,()->sender.send("reader@example.com","email","123456"));
        assertEquals("验证码发送失败，请稍后重试",error.getMessage()); assertNull(error.getCause());
        when(http.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenThrow(new InterruptedException());
        try {
            assertThrows(IllegalStateException.class,()->sender.send("reader@example.com","email","123456"));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {Thread.interrupted();}
    }
}
