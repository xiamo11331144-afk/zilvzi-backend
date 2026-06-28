package com.zilvzi.mapper;

import com.zilvzi.pojo.FileUpload;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 文件上传Mapper接口
 */
public interface FileUploadMapper {

    /**
     * 插入文件记录
     */
    int insert(FileUpload fileUpload);

    /**
     * 根据ID查询
     */
    FileUpload selectById(@Param("id") Long id);

    /**
     * 查询用户的文件列表
     */
    List<FileUpload> selectByUserId(@Param("userId") Long userId);

    /**
     * 根据关联查询
     */
    List<FileUpload> selectByRelated(@Param("relatedType") String relatedType, @Param("relatedId") Long relatedId);
}