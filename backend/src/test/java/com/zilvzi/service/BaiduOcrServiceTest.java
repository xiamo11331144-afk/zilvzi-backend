package com.zilvzi.service;

import com.zilvzi.service.impl.OcrServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.http.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class BaiduOcrServiceTest {
    String body(HttpRequest request) {
        var output = new java.io.ByteArrayOutputStream();
        var done = new java.util.concurrent.CompletableFuture<String>();
        request.bodyPublisher().orElseThrow().subscribe(new java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer>() {
            public void onSubscribe(java.util.concurrent.Flow.Subscription s) { s.request(Long.MAX_VALUE); }
            public void onNext(java.nio.ByteBuffer data) { byte[] bytes = new byte[data.remaining()]; data.get(bytes); output.writeBytes(bytes); }
            public void onError(Throwable error) { done.completeExceptionally(error); }
            public void onComplete() { done.complete(output.toString(java.nio.charset.StandardCharsets.UTF_8)); }
        });
        return done.join();
    }

    @Test void uploadedImagesAndPdfUseBinaryParametersInsteadOfOssUrl() throws Exception {
        when(client.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(
                response("{\"access_token\":\"token\",\"expires_in\":3600}"),response("{\"words_result\":[{\"words\":\"课程\"}]}"));
        assertEquals("课程",service.recognizeFile(new byte[]{1,2,3},"schedule.jpg"));
        assertEquals("课程",service.recognizeFile(new byte[]{4,5,6},"schedule.pdf"));
        var requests=org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(client,times(3)).send(requests.capture(),any(HttpResponse.BodyHandler.class));
        String image=body(requests.getAllValues().get(1)), pdf=body(requests.getAllValues().get(2));
        assertTrue(image.startsWith("image=")); assertFalse(image.contains("url="));
        assertTrue(pdf.startsWith("pdf_file=")); assertTrue(pdf.contains("pdf_file_num=1"));
    }

    @Test void emptyFileIsRejectedBeforeNetworkCalls() {
        assertThrows(IllegalArgumentException.class,()->service.recognizeFile(new byte[0],"schedule.jpg"));
        verifyNoInteractions(client);
    }

    HttpClient client = mock(HttpClient.class);
    OcrServiceImpl service = new OcrServiceImpl();
    BaiduOcrServiceTest() {
        ReflectionTestUtils.setField(service,"httpClient",client);
        ReflectionTestUtils.setField(service,"apiKey","test-key");
        ReflectionTestUtils.setField(service,"secretKey","test-secret");
    }
    HttpResponse<String> response(String body) {
        return new HttpResponse<String>() {
            public int statusCode() { return 200; }
            public HttpRequest request() { return null; }
            public java.util.Optional<HttpResponse<String>> previousResponse() { return java.util.Optional.empty(); }
            public HttpHeaders headers() { return HttpHeaders.of(java.util.Map.of(),(a,b)->true); }
            public String body() { return body; }
            public java.util.Optional<javax.net.ssl.SSLSession> sslSession() { return java.util.Optional.empty(); }
            public java.net.URI uri() { return java.net.URI.create("https://example.com"); }
            public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        };
    }
    @Test void parsesWordsAndCachesToken() throws Exception {
        when(client.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(
                response("{\"access_token\":\"test-token\",\"expires_in\":3600}"),
                response("{\"words_result\":[{\"words\":\"星期一\"},{\"words\":\"计算机课程\"}]}"));
        assertEquals("星期一\n计算机课程", service.recognizeText("https://example.com/schedule.png"));
        service.recognizeText("https://example.com/second.png");
        verify(client,times(3)).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
    }
    @Test void authenticationFailureDoesNotReturnEmptySuccessOrExposeProviderResponse() throws Exception {
        when(client.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class)))
                .thenReturn(response("{\"error\":\"invalid_client\",\"error_description\":\"sensitive-provider-detail\"}"));
        var error = assertThrows(IllegalStateException.class, () -> service.recognizeText("https://example.com/a.png"));
        assertTrue(error.getMessage().contains("鉴权失败"));
        assertFalse(error.getMessage().contains("sensitive-provider-detail"));
    }
    @Test void expiredTokenRefreshesOnceAndOcrBusinessFailureIsNotSuccess() throws Exception {
        when(client.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(
                response("{\"access_token\":\"old\",\"expires_in\":3600}"), response("{\"error_code\":111}"),
                response("{\"access_token\":\"new\",\"expires_in\":3600}"), response("{\"error_code\":17}"));
        var error = assertThrows(IllegalStateException.class, () -> service.recognizeText("https://example.com/a.png"));
        assertTrue(error.getMessage().contains("17"));
        verify(client,times(4)).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
    }
    @Test void emptyRecognitionIsDistinctFromMalformedResponse() throws Exception {
        when(client.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(
                response("{\"access_token\":\"token\",\"expires_in\":3600}"),response("{\"words_result\":[]}"),response("{}"));
        assertEquals("",service.recognizeText("https://example.com/a.png"));
        assertThrows(IllegalStateException.class,()->service.recognizeText("https://example.com/b.png"));
    }
}
