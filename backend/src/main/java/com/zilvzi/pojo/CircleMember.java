package com.zilvzi.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 圈子成员实体类
 */
@Data
public class CircleMember {
    private Long id;
    private Long circleId;
    private Long userId;
    private Integer role; // 0-普通成员 1-管理员
    private Integer status; // 0-待审核 1-已加入
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime joinTime;

    // 非数据库字段
    private String nickname;
    private String avatar;

}