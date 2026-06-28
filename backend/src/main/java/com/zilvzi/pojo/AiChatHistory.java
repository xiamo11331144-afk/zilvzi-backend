package com.zilvzi.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * AI对话历史记录
 */
@Data
public class AiChatHistory {
    private Long id;
    private Long userId;
    private String role;        // user / assistant
    private String content;
    private String sessionType; // chat / advice
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}