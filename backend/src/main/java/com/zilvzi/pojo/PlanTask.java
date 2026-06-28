package com.zilvzi.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 计划任务实体类
 */
@Data
public class PlanTask {
    private Long id;
    private Long planId;
    private Integer dayNumber; // 第几天
    private LocalDate taskDate;
    private String taskName;
    private String taskType; // 背单词, 听力练习, 阅读理解, etc.
    private String taskDescription;
    private String taskDuration; // 预计时长
    private String taskIcon;
    private String taskColor;
    private Integer sortOrder;
    private Integer completed; // -1-断卡/过期未完成 0-未完成 1-已完成
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    
    /**
     * 获取状态文本，用于前端显示
     */
    public String getStatusText() {
        if (completed == null) return "未完成";
        switch (completed) {
            case -1: return "未打卡";  // 断卡/过期未完成
            case 0: return "未完成";
            case 1: return "已完成";
            default: return "未知";
        }
    }
    
    /**
     * 判断是否为断卡状态
     */
    public boolean isStreakBreak() {
        return completed != null && completed == -1;
    }
}