package com.zilvzi.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Topic {
    private Long id;
    private Long circleId;
    private Long userId;
    private String title;
    private String content;
    private Integer isPinned;
    private Integer replyCount;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    private String nickname;
    private String avatar;
}
