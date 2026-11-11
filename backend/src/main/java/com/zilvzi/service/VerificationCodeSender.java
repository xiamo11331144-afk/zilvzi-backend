package com.zilvzi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** Bridge to the deployment's SMS/email provider. Never log the payload or provider response. */
@Service
public class VerificationCodeSender {
    @Value("${verification.delivery.url:}")
    private String url;
    @Value("${verification.delivery.token:}")
    private String token;
    private HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = new ObjectMapper();

    public void send(String target, String type, String code) {
        if (url == null || url.isBlank() || token == null || token.isBlank()) {
            throw new IllegalStateException("验证码发送服务未配置，请使用账号密码或微信登录");
        }
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("Invalid delivery endpoint");
            }
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of(
                            "target", target, "type", type, "code", code, "expiresInSeconds", 300))))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Delivery rejected");
            }
            JsonNode acknowledgement = json.readTree(response.body());
            if (acknowledgement == null || !acknowledgement.path("accepted").isBoolean()
                    || !acknowledgement.path("accepted").booleanValue()) {
                throw new IllegalStateException("Delivery not acknowledged");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("验证码发送中断，请稍后重试");
        } catch (Exception e) {
            // Provider errors and request URIs may contain credentials. Do not expose the original exception.
            throw new IllegalStateException("验证码发送失败，请稍后重试");
        }
    }
}
