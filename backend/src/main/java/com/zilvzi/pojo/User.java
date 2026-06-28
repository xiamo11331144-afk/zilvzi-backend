package com.zilvzi.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体类
 */
@Data
public class User {
    private Long id;
    private String username;
    @JsonIgnore
    private String password;
    private String phone;
    private String email;
    private String nickname;
    private String avatar;
    private String bio;
    private Integer level;
    private Integer experience;
    private Integer role; // 0-普通用户 1-管理员
    private String openid; // 微信openid
    private String province; // 省份（用于搭子匹配地域筛选）
    private String city; // 城市（用于搭子匹配地域筛选）
    private String district; // 区/县（用于详细地址展示）
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

}