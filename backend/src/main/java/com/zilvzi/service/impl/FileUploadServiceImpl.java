package com.zilvzi.service.impl;

import com.zilvzi.mapper.FileUploadMapper;
import com.zilvzi.pojo.FileUpload;
import com.zilvzi.service.FileUploadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * 文件上传Service实现类
 */
@Service
public class FileUploadServiceImpl implements FileUploadService {

    @Autowired
    private FileUploadMapper fileUploadMapper;

    @Value("${upload.path}")
    private String uploadPath;

    @Value("${upload.url}")
    private String uploadUrl;

    @Override
    public FileUpload uploadFile(Long userId, MultipartFile file, String relatedType, Long relatedId) {
        // 生成唯一文件名
        String originalName = file.getOriginalFilename();
        String extension = originalName.substring(originalName.lastIndexOf("."));
        String fileName = UUID.randomUUID().toString() + extension;

        // 创建上传目录
        File dir = new File(uploadPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // 保存文件
        try {
            file.transferTo(new File(uploadPath + fileName));
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败: " + e.getMessage());
        }

        // 保存记录
        FileUpload fileUpload = new FileUpload();
        fileUpload.setUserId(userId);
        fileUpload.setFileName(originalName);
        fileUpload.setFilePath(uploadUrl + fileName);
        fileUpload.setFileType(extension.substring(1));
        fileUpload.setFileSize(file.getSize());
        fileUpload.setRelatedType(relatedType);
        fileUpload.setRelatedId(relatedId);

        fileUploadMapper.insert(fileUpload);

        return fileUpload;
    }

    @Override
    public List<FileUpload> getUserFiles(Long userId) {
        return fileUploadMapper.selectByUserId(userId);
    }

    @Override
    public FileUpload getFileById(Long id) {
        return fileUploadMapper.selectById(id);
    }
}