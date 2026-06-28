package com.zilvzi.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.zilvzi.service.OssService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

/**
 * OSS服务实现类
 */
@Service
public class OssServiceImpl implements OssService {

    @Value("${aliyun.oss.endpoint}")
    private String endpoint;

    @Value("${aliyun.oss.access-key-id}")
    private String accessKeyId;

    @Value("${aliyun.oss.access-key-secret}")
    private String accessKeySecret;

    @Value("${aliyun.oss.bucket-name}")
    private String bucketName;

    @Value("${aliyun.oss.url-prefix}")
    private String urlPrefix;

    // 允许的文件扩展名白名单
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        "jpg", "jpeg", "png", "gif", "bmp", "webp",
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx"
    );

    // 危险的文件扩展名黑名单
    private static final Set<String> DANGEROUS_EXTENSIONS = Set.of(
        "exe", "bat", "sh", "cmd", "com", "msi", "jsp", "php", "py", "rb", "jar", "war", "shell", "bash"
    );

    @Override
    public String uploadFile(MultipartFile file, String directory) {
        OSS ossClient = null;
        try {
            // 创建OSS客户端
            ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);

            // 获取文件名
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            String ext = extension.substring(1).toLowerCase();

            // 文件类型校验
            if (DANGEROUS_EXTENSIONS.contains(ext)) {
                throw new RuntimeException("不允许上传此类型的文件: " + ext);
            }
            if (!ALLOWED_EXTENSIONS.contains(ext)) {
                throw new RuntimeException("不支持的文件类型: " + ext);
            }

            // 生成唯一文件名
            String datePath = new SimpleDateFormat("yyyy/MM/dd").format(new Date());
            String fileName = directory + "/" + datePath + "/" + UUID.randomUUID().toString() + extension;

            // 上传文件
            InputStream inputStream = file.getInputStream();
            ossClient.putObject(bucketName, fileName, inputStream);

            // 返回文件访问URL
            return urlPrefix + "/" + fileName;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("文件上传失败: " + e.getMessage());
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }
    }

    @Override
    public void deleteFile(String fileUrl) {
        OSS ossClient = null;
        try {
            ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);

            // 从URL中提取文件名
            String fileName = fileUrl.replace(urlPrefix + "/", "");

            // 删除文件
            ossClient.deleteObject(bucketName, fileName);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("文件删除失败: " + e.getMessage());
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }
    }
}