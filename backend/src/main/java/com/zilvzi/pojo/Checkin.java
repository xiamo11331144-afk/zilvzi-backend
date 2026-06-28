package com.zilvzi.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 打卡记录实体类
 */
@Data
public class Checkin {
    private Long id;
    private Long userId;
    private Long goalId;
    private Long taskId;
    private String content; // 打卡内容/备注
    private String image; // 打卡图片
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime checkinTime;
    private String date; // 打卡日期 yyyy-MM-dd

    // 互助审核字段
    private String status;        // pending/approved/rejected（默认 approved，兼容自打卡）
    private Long partnerId;       // 审核搭子的 userId
    private String proofImage;    // 凭证图片
    private String proofContent;  // 凭证文字描述
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime reviewTime;  // 审核时间
    private String reviewComment; // 审核评语

    // 任务相关字段（用于今日任务查询）
    private String taskName;
    private String taskType;
    private String taskDescription;
    private String taskDuration;
    private Integer completed; // 0-未完成 1-已完成
    private Integer expired; // 0-未过期 1-已过期
    private String taskDate; // 任务日期
    private String checkinStatus; // 打卡审核状态（pending/approved/rejected）

}