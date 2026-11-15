package com.zilvzi.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zilvzi.service.OcrService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

@Service
public class OcrServiceImpl implements OcrService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OcrServiceImpl.class);
    @Value("${baidu.ocr.api-key:}") private String apiKey;
    @Value("${baidu.ocr.secret-key:}") private String secretKey;
    private HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();
    private String accessToken;
    private Instant tokenExpiresAt = Instant.EPOCH;

    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

    private JsonNode post(String endpoint, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            log.warn("[OCR] phase={} httpStatus={}", endpoint.contains("/oauth/") ? "auth" : "recognition", response.statusCode());
            throw new IllegalStateException("百度OCR服务请求失败（HTTP " + response.statusCode() + "），请稍后重试");
        }
        return json.readTree(response.body());
    }

    private synchronized String token() throws Exception {
        if (accessToken != null && Instant.now().isBefore(tokenExpiresAt)) return accessToken;
        if (apiKey == null || apiKey.isBlank() || secretKey == null || secretKey.isBlank()) throw new IllegalStateException("百度OCR凭据未配置");
        JsonNode result = post("https://aip.baidubce.com/oauth/2.0/token",
                "grant_type=client_credentials&client_id=" + encode(apiKey) + "&client_secret=" + encode(secretKey));
        String value = result.path("access_token").asText("");
        if (value.isBlank()) {
            log.warn("[OCR] phase=auth outcome=failed");
            throw new IllegalStateException("百度OCR鉴权失败，请检查API Key和Secret Key");
        }
        accessToken = value;
        tokenExpiresAt = Instant.now().plusSeconds(Math.max(0, result.path("expires_in").asLong(0) - 60));
        return value;
    }

    private synchronized void invalidateToken() { accessToken = null; tokenExpiresAt = Instant.EPOCH; }

    @Override
    public String recognizeText(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) throw new IllegalArgumentException("OCR图片地址不能为空");
        return recognize("url=" + encode(imageUrl));
    }

    @Override
    public String recognizeFile(byte[] content, String filename) {
        if (content == null || content.length == 0) throw new IllegalArgumentException("课表文件为空，请重新选择");
        if (content.length > 10 * 1024 * 1024) throw new IllegalArgumentException("课表文件过大，请压缩后重试");
        boolean pdf = filename != null && filename.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf");
        String encoded = encode(java.util.Base64.getEncoder().encodeToString(content));
        if (encoded.length() > 10 * 1024 * 1024) throw new IllegalArgumentException("课表文件编码后超过10MB，请压缩后重试");
        return recognize((pdf ? "pdf_file=" : "image=") + encoded + (pdf ? "&pdf_file_num=1" : ""));
    }

    private String recognize(String payload) {
        try {
            for (int attempt = 0; attempt < 2; attempt++) {
                JsonNode result = post("https://aip.baidubce.com/rest/2.0/ocr/v1/accurate_basic?access_token=" + encode(token()),
                        payload + "&detect_direction=true");
                if (result.has("error_code")) {
                    int code = result.path("error_code").asInt();
                    if ((code == 110 || code == 111) && attempt == 0) { invalidateToken(); continue; }
                    log.warn("[OCR] phase=recognition providerCode={} logId={}", code, result.path("log_id").asText("unavailable"));
                    throw new IllegalStateException("百度OCR识别失败（错误码：" + code + "），请检查服务权限、额度及图片访问权限");
                }
                JsonNode words = result.get("words_result");
                if (words == null || !words.isArray()) throw new IllegalStateException("百度OCR返回格式异常，请稍后重试");
                StringBuilder text = new StringBuilder();
                for (JsonNode row : words) {
                    String line = row.path("words").asText("").trim();
                    if (!line.isEmpty()) { if (!text.isEmpty()) text.append('\n'); text.append(line); }
                }
                log.info("[OCR] phase=recognition outcome=success lines={} characters={}", words.size(), text.length());
                return text.toString();
            }
            throw new IllegalStateException("百度OCR鉴权失败，请重试");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OCR识别已中断，请重试");
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[OCR] phase=transport exceptionType={}", e.getClass().getSimpleName());
            throw new IllegalStateException("百度OCR网络或响应异常，请稍后重试");
        }
    }
}
