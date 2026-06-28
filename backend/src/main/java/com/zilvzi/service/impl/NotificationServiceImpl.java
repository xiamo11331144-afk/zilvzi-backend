package com.zilvzi.service.impl;

import com.zilvzi.mapper.NotificationMapper;
import com.zilvzi.pojo.Notification;
import com.zilvzi.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 消息通知Service实现类
 */
@Service
public class NotificationServiceImpl implements NotificationService {

    @Autowired
    private NotificationMapper notificationMapper;

    @Override
    public void sendNotification(Long userId, Long fromUserId, String type, String title, String content, String relatedType, Long relatedId) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setFromUserId(fromUserId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setRelatedType(relatedType);
        notification.setRelatedId(relatedId);
        notification.setIsRead(0);

        notificationMapper.insert(notification);
    }

    @Override
    public List<Notification> getUserNotifications(Long userId, Integer page, Integer size) {
        if (page < 1) page = 1;
        if (size < 1) size = 10;
        Integer offset = (page - 1) * size;
        return notificationMapper.selectByUserId(userId, offset, size);
    }

    @Override
    public Integer getUnreadCount(Long userId) {
        return notificationMapper.countUnread(userId);
    }

    @Override
    public Integer getUnreadCountByType(Long userId, String type) {
        return notificationMapper.countUnreadByType(userId, type);
    }

    @Override
    public void markAsRead(Long id) {
        notificationMapper.markAsRead(id);
    }

    @Override
    public void markAllAsRead(Long userId) {
        notificationMapper.markAllAsRead(userId);
    }

    @Override
    public void markAllAsReadByType(Long userId, String type) {
        notificationMapper.markAllAsReadByType(userId, type);
    }

    @Override
    public void clearAll(Long userId) {
        notificationMapper.deleteByUserId(userId);
    }

    @Override
    public void deleteById(Long id) {
        notificationMapper.deleteById(id);
    }
}