package com.zilvzi.controller;

import com.zilvzi.service.OcrService;
import com.zilvzi.service.OssService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ScheduleOcrUploadTest {
    OssService oss=mock(OssService.class);
    OcrService ocr=mock(OcrService.class);
    FileUploadController controller=new FileUploadController();
    MockMultipartFile file=new MockMultipartFile("file","schedule.jpg","image/jpeg",new byte[]{1,2,3});
    ScheduleOcrUploadTest() {
        ReflectionTestUtils.setField(controller,"ossService",oss);
        ReflectionTestUtils.setField(controller,"ocrService",ocr);
    }
    @Test void uploadPassesOriginalFileBytesToOcrEvenForPrivateOssUrl() throws Exception {
        when(oss.uploadFile(file,"schedule")).thenReturn("https://private.example.com/a.jpg");
        when(ocr.recognizeFile(any(byte[].class),eq("schedule.jpg"))).thenReturn("星期一 数学");
        var result=controller.uploadSchedule(1L,file);
        assertEquals(200,result.getCode());
        var bytes=org.mockito.ArgumentCaptor.forClass(byte[].class);
        verify(ocr).recognizeFile(bytes.capture(),eq("schedule.jpg"));
        assertArrayEquals(file.getBytes(),bytes.getValue());
        verify(ocr,never()).recognizeText(anyString());
    }
    @Test void recognitionFailureReturnsTraceAndIsNotEmptySuccess() {
        when(oss.uploadFile(file,"schedule")).thenReturn("https://example.com/a.jpg");
        when(ocr.recognizeFile(any(),any())).thenThrow(new IllegalStateException("百度OCR识别失败（错误码：17）"));
        var result=controller.uploadSchedule(1L,file);
        assertNotEquals(200,result.getCode());
        assertTrue(result.getMessage().contains("17"));
        assertTrue(result.getMessage().contains("排查编号"));
    }
    @Test void uploadFailureDoesNotInvokeOcrOrLeakRawProviderDetails() {
        when(oss.uploadFile(file,"schedule")).thenThrow(new RuntimeException("sensitive-provider-detail"));
        var result=controller.uploadSchedule(1L,file);
        assertNotEquals(200,result.getCode());
        assertFalse(result.getMessage().contains("sensitive-provider-detail"));
        verifyNoInteractions(ocr);
    }
}
