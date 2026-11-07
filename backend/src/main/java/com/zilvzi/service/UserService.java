package com.zilvzi.service;

import com.zilvzi.pojo.User;

import java.util.Map;

/**
 * 用户Service接口
 */
public interface UserService {

    /**
     * 用户注册
     */
    User register(String username, String password, String phone, String email, String nickname, String province, String city, String district);

    /**
     * 用户登录（手机号+验证码）
     */
    User loginByPhone(String phone, String code);

    /**
     * 用户登录（邮箱+验证码）
     */
    User loginByEmail(String email, String code);

    /**
     * 用户登录（用户名+密码）
     */
    User loginByUsername(String username, String password);

    /**
     * 微信登录
     */
    User loginByWechat(String code, String province, String city, String district);

    /**
     * 发送验证码
     */
    void sendVerifyCode(String target, String type);

    /**
     * 根据ID获取用户
     */
    User getUserById(Long id);

    /**
     * 更新用户信息
     */
    void updateUser(User user);

    /**
     * 更新用户头像
     */
    void updateAvatar(Long id, String avatar);

    Map<String, Object> getMentorStatus(Long userId);

    void setMentorEnabled(Long userId, boolean enabled);
}