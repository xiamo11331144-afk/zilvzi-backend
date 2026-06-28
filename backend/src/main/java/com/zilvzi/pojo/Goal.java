package com.zilvzi.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 目标实体类
 */
@Data
public class Goal {
    private Long id;
    private Long userId;
    private String name;
    private String type; // exam, study, fitness, reading, skill, habit
    private String description;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status; // active, completed, abandoned
    private Integer streak; // 连续打卡天数
    private Integer totalCheckins; // 总打卡天数
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

}