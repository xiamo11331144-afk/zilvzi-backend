package com.zilvzi.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 动态实体类
 */
@Data
public class Post {
    private Long id;
    private Long userId;
    private Long circleId;
    private String content;
    private String images; // JSON数组
    private Integer likeCount;
    private Integer commentCount;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    // 关联字段（非数据库字段）
    private String nickname;
    private String avatar;
    private Boolean isLiked; // 当前用户是否点赞

}