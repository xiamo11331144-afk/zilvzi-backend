package com.zilvzi.service;

import com.zilvzi.pojo.FileUpload;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

/**
 * 文件上传Service接口
 */
public interface FileUploadService {

    /**
     * 上传文件
     */
    FileUpload uploadFile(Long userId, MultipartFile file, String relatedType, Long relatedId);

    /**
     * 获取用户的文件列表
     */
    List<FileUpload> getUserFiles(Long userId);

    /**
     * 获取文件详情
     */
    FileUpload getFileById(Long id);
}