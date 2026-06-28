package com.zilvzi.service;

/**
 * OCR服务接口
 */
public interface OcrService {

    /**
     * 识别图片中的文字
     * @param imageUrl 图片URL
     * @return 识别出的文字
     */
    String recognizeText(String imageUrl);
}