package com.zilvzi.service.impl;

import com.zilvzi.mapper.NotificationMapper;
import com.zilvzi.mapper.MessageMapper;
import com.zilvzi.mapper.PartnerMapper;
import com.zilvzi.pojo.Notification;
import com.zilvzi.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 消息通知Service实现类
 */
@Service
public class NotificationServiceImpl implements NotificationService {

    @Autowired
    private NotificationMapper notificationMapper;
    @Autowired
    private MessageMapper messageMapper;
    @Autowired
    private PartnerMapper partnerMapper;

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
        if (page == null || page < 1) page = 1;
        if (size == null || size < 1) size = 20;
        size = Math.min(size, 100);
        Integer offset = (page - 1) * size;
        return notificationMapper.selectByUserId(userId, offset, size);
    }

    @Override
    public Map<String, Object> getUserNotifications(Long userId, Integer page, Integer size, String category) {
        String normalizedCategory = normalizeCategory(category);
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        int offset = safeOffset(safePage, safeSize);
        List<Notification> rows = notificationMapper.selectByUserIdAndCategory(
                userId, normalizedCategory, offset, safeSize + 1);
        boolean hasMore = rows.size() > safeSize;
        if (hasMore) rows = rows.subList(0, safeSize);
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("list", rows);
        result.put("unreadCount", notificationMapper.countUnreadByCategory(userId, normalizedCategory));
        result.put("hasMore", hasMore);
        return result;
    }

    @Override
    public Integer getUnreadCountForCategory(Long userId, String category) {
        return notificationMapper.countUnreadByCategory(userId, normalizeCategory(category));
    }

    @Override
    public void markAllAsReadByCategory(Long userId, String category) {
        notificationMapper.markAllAsReadByCategory(userId, normalizeCategory(category));
    }

    @Override
    public void clearAllByCategory(Long userId, String category) {
        notificationMapper.deleteByCategory(userId, normalizeCategory(category));
    }

    private int safeOffset(int page, int size) {
        long offset = ((long) page - 1L) * size;
        return (int) Math.min(offset, Integer.MAX_VALUE);
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank() || "all".equals(category)) return "all";
        if ("notice".equals(category) || "circle".equals(category)) return category;
        throw new IllegalArgumentException("通知分类仅支持notice、circle或all");
    }

    @Override
    public Map<String, Object> getBadges(Long userId) {
        Map<String, Object> notificationCounts = notificationMapper.selectUnreadBadgeCounts(userId);
        if (notificationCounts == null) notificationCounts = java.util.Collections.emptyMap();
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("notificationUnread", Math.max(0, asInt(notificationCounts.get("notificationUnread"))));
        result.put("circleUnread", Math.max(0, asInt(notificationCounts.get("circleUnread"))));
        Integer privateUnread = messageMapper.countUnread(userId);
        result.put("privateUnread", Math.max(0, privateUnread == null ? 0 : privateUnread));
        result.put("pendingRequests", Math.max(0, partnerMapper.countPendingReceivedRequests(userId)));
        return result;
    }

    private int asInt(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
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