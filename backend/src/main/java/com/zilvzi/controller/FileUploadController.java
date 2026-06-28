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
        try {
            // 1. 上传到OSS
            String fileUrl = ossService.uploadFile(file, "schedule");

            // 2. 调用OCR识别
            String recognizedText = ocrService.recognizeText(fileUrl);

            Map<String, Object> result = new HashMap<>();
            result.put("url", fileUrl);
            result.put("name", file.getOriginalFilename());
            result.put("recognizedText", recognizedText);

            return Result.success("上传并识别成功", result);
        } catch (Exception e) {
            return Result.error("处理失败: " + e.getMessage());
        }
    }
}