package com.zilvzi.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 计划实体类
 */
@Data
public class Plan {
    private Long id;
    private Long userId;
    private Long goalId;
    private String name;
    private String goalType; // 目标类型
    private Integer totalDays;
    private Integer completedDays;
    private String scheduleData; // 课表/时间安排JSON
    private String remark; // 用户备注
    private String outline; // 计划大纲JSON（L1路线图）
    private Integer currentPhase; // 当前阶段
    private String status; // active, completed, paused
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    // 断卡统计（瞬态字段，不存DB，由Service层填充）
    private Integer streakBreakDays; // 断卡天数
    private Integer pendingDays;     // 过期未打卡天数
    private Boolean feasible;        // 计划是否可行
    private String infeasibleReason; // 不可行原因

}