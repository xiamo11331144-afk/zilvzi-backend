package com.zilvzi.service.impl;

import com.aliyun.ocr_api20210707.Client;
import com.aliyun.ocr_api20210707.models.RecognizeAdvancedRequest;
import com.aliyun.ocr_api20210707.models.RecognizeAdvancedResponse;
import com.aliyun.teaopenapi.models.Config;
import com.zilvzi.service.OcrService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * OCR服务实现类（阿里云文字识别）
 */
@Service
public class OcrServiceImpl implements OcrService {

    @Value("${aliyun.ocr.access-key-id}")
    private String accessKeyId;

    @Value("${aliyun.ocr.access-key-secret}")
    private String accessKeySecret;

    @Override
    public String recognizeText(String imageUrl) {
        try {
            // 创建OCR客户端
            Config config = new Config()
                    .setAccessKeyId(accessKeyId)
                    .setAccessKeySecret(accessKeySecret)
                    .setEndpoint("ocr-api.cn-hangzhou.aliyuncs.com");

            Client client = new Client(config);

            // 创建识别请求
            RecognizeAdvancedRequest request = new RecognizeAdvancedRequest()
                    .setUrl(imageUrl)
                    .setOutputCharInfo(true)
                    .setOutputTable(true);

            // 调用OCR接口
            RecognizeAdvancedResponse response = client.recognizeAdvanced(request);

            // 解析结果
            return parseOcrResult(response.getBody().getData());
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("[OCR] 识别失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 解析OCR结果
     */
    private String parseOcrResult(String data) {
        // 简单提取文字内容
        // 实际项目中需要根据OCR返回的JSON格式进行解析
        if (data == null || data.isEmpty()) {
            return null;
        }

        // 提取文字内容（简化处理）
        StringBuilder text = new StringBuilder();
        try {
            // 解析JSON获取文字
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(data);

            // 获取识别的文字
            if (root.has("content")) {
                text.append(root.get("content").asText());
            } else if (root.has("blocks")) {
                com.fasterxml.jackson.databind.JsonNode blocks = root.get("blocks");
                for (com.fasterxml.jackson.databind.JsonNode block : blocks) {
                    if (block.has("text")) {
                        text.append(block.get("text").asText()).append("\n");
                    }
                }
            }
        } catch (Exception e) {
            // 如果解析失败，直接返回原始数据
            return data;
        }

        return text.toString();
    }
}