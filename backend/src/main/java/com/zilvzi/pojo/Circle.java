package com.zilvzi.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 圈子实体类
 */
@Data
public class Circle {
    private Long id;
    private Long creatorId;
    private String name;
    private String description;
    private String cover;
    private String tags;
    private Integer memberCount;
    private Integer postCount;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    // 创建者信息（非数据库字段，由 Service 填充）
    private String creatorNickname;
    private String creatorAvatar;
}