package com.zilvzi.service;

import com.zilvzi.pojo.Notification;
import java.util.List;
import java.util.Map;

/**
 * 消息通知Service接口
 */
public interface NotificationService {

    /**
     * 发送通知
     */
    void sendNotification(Long userId, Long fromUserId, String type, String title, String content, String relatedType, Long relatedId);

    /**
     * 获取用户通知列表
     */
    List<Notification> getUserNotifications(Long userId, Integer page, Integer size);

    /**
     * 获取未读数量
     */
    Integer getUnreadCount(Long userId);

    /**
     * 获取指定类型的未读数量
     */
    Integer getUnreadCountByType(Long userId, String type);

    /**
     * 标记已读
     */
    void markAsRead(Long id);

    /**
     * 标记所有已读
     */
    void markAllAsRead(Long userId);

    void markAllAsReadByType(Long userId, String type);

    /**
     * 清除用户所有通知
     */
    void clearAll(Long userId);

    /**
     * 删除单条通知
     */
    void deleteById(Long id);
}