package com.zilvzi.controller;

import com.zilvzi.pojo.Result;
import com.zilvzi.service.OcrService;
import com.zilvzi.service.OssService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * 文件上传Controller
 */
@RestController
@RequestMapping("/api/file")
public class FileUploadController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FileUploadController.class);

    @Autowired
    private OssService ossService;

    @Autowired
    private OcrService ocrService;

    /**
     * 上传文件到OSS
     */
    @PostMapping("/upload")
    public Result<?> uploadFile(@RequestParam("file") MultipartFile file,
                                @RequestParam(value = "directory", defaultValue = "schedule") String directory) {
        try {
            System.out.println("[Upload] ====== 文件上传开始 ======");
            System.out.println("[Upload] 文件名: " + file.getOriginalFilename());
            System.out.println("[Upload] 文件大小: " + file.getSize() + " bytes");
            System.out.println("[Upload] Content-Type: " + file.getContentType());
            System.out.println("[Upload] 目录: " + directory);

            String fileUrl = ossService.uploadFile(file, directory);

            System.out.println("[Upload] ✅ 上传成功, URL: " + fileUrl);
            System.out.println("[Upload] ====== 文件上传结束 ======");

            Map<String, Object> result = new HashMap<>();
            result.put("url", fileUrl);
            result.put("name", file.getOriginalFilename());

            return Result.success("上传成功", result);
        } catch (Exception e) {
            System.out.println("[Upload] ❌ 上传失败: " + e.getMessage());
            e.printStackTrace();
            return Result.error("上传失败: " + e.getMessage());
        }
    }

    /**
     * 上传课表并识别
     */
    @PostMapping("/upload-schedule")
    public Result<?> uploadSchedule(@RequestAttribute("userId") Long userId,
                                    @RequestParam("file") MultipartFile file) {
        String traceId = java.util.UUID.randomUUID().toString().substring(0, 8);
        String stage = "upload";
        try {
            log.info("[ScheduleUpload] trace={} stage=received bytes={}", traceId, file.getSize());
            // 1. 上传到OSS
            String fileUrl = ossService.uploadFile(file, "schedule");

            // 2. 调用OCR识别
            stage = "ocr";
            log.info("[ScheduleUpload] trace={} stage=ocr input=binary", traceId);
            String recognizedText = ocrService.recognizeFile(file.getBytes(), file.getOriginalFilename());

            Map<String, Object> result = new HashMap<>();
            result.put("url", fileUrl);
            result.put("name", file.getOriginalFilename());
            result.put("recognizedText", recognizedText);
            result.put("traceId", traceId);
            log.info("[ScheduleUpload] trace={} stage=complete characters={}", traceId, recognizedText == null ? 0 : recognizedText.length());

            return Result.success(recognizedText == null || recognizedText.isBlank()
                    ? "文件已上传，但未识别到文字" : "上传并识别成功", result);
        } catch (Exception e) {
            log.warn("[ScheduleUpload] trace={} stage={} exceptionType={}", traceId, stage, e.getClass().getSimpleName());
            String message = "ocr".equals(stage) && e instanceof IllegalStateException ? e.getMessage() :
                    "ocr".equals(stage) && e instanceof IllegalArgumentException ? e.getMessage() :
                    "upload".equals(stage) ? "文件上传失败，请检查对象存储配置" : "课表读取或识别失败，请重试";
            return Result.error(message + "（排查编号：" + traceId + "）");
        }
    }
}
