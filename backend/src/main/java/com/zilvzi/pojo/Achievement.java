package com.zilvzi.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 成就/勋章实体类
 */
@Data
public class Achievement {
    private Long id;
    private Long userId;
    private String medalName;
    private String medalIcon;
    private String medalColor;
    private String condition; // 达成条件描述
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime unlockTime;

}