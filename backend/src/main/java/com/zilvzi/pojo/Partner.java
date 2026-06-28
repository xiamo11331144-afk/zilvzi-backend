package com.zilvzi.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 搭子关系实体类
 */
@Data
public class Partner {
    private Long id;
    private Long userId;
    private Long partnerId;
    private Long goalId;
    private String status; // pending, accepted, rejected, blocked
    private String prevStatus; // 拉黑前的状态
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

}