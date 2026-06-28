package com.zilvzi.controller;

import com.zilvzi.pojo.Notification;
import com.zilvzi.pojo.Result;
import com.zilvzi.service.NotificationService;
import com.zilvzi.mapper.NotificationMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息通知Controller
 */
@RestController
@RequestMapping("/api/notification")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationMapper notificationMapper;

    /**
     * 获取通知列表
     */
    @GetMapping("/list")
    public Result<?> getNotifications(@RequestAttribute("userId") Long userId,
                                      @RequestParam(defaultValue = "1") Integer page,
                                      @RequestParam(defaultValue = "20") Integer size) {
        try {
            List<Notification> notifications = notificationService.getUserNotifications(userId, page, size);
            Integer unreadCount = notificationService.getUnreadCount(userId);

            Map<String, Object> result = new HashMap<>();
            result.put("list", notifications);
            result.put("unreadCount", unreadCount);

            return Result.success(result);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取未读数量
     */
    @GetMapping("/unread/count")
    public Result<?> getUnreadCount(@RequestAttribute("userId") Long userId) {
        try {
            Integer count = notificationService.getUnreadCount(userId);
            return Result.success(count);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取指定类型的未读数量
     */
    @GetMapping("/unread/count/{type}")
    public Result<?> getUnreadCountByType(@RequestAttribute("userId") Long userId, @PathVariable String type) {
        try {
            Integer count = notificationService.getUnreadCountByType(userId, type);
            return Result.success(count);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 标记已读
     */
    @PutMapping("/read/{id}")
    public Result<?> markAsRead(@RequestAttribute("userId") Long userId, @PathVariable Long id) {
        try {
            // 校验通知归属
            Notification notification = notificationMapper.selectById(id);
            if (notification == null || !notification.getUserId().equals(userId)) {
                return Result.error(403, "无权操作此通知");
            }
            notificationService.markAsRead(id);
            return Result.success("已标记已读");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 标记所有已读
     */
    @PutMapping("/read/all")
    public Result<?> markAllAsRead(@RequestAttribute("userId") Long userId) {
        try {
            notificationService.markAllAsRead(userId);
            return Result.success("已全部标记已读");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 标记指定类型全部已读
     */
    @PutMapping("/read/type/{type}")
    public Result<?> markAllAsReadByType(@RequestAttribute("userId") Long userId, @PathVariable String type) {
        try {
            notificationService.markAllAsReadByType(userId, type);
            return Result.success("已标记已读");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 清除所有通知
     */
    @DeleteMapping("/clear")
    public Result<?> clearAll(@RequestAttribute("userId") Long userId) {
        try {
            notificationService.clearAll(userId);
            return Result.success("已清除全部通知");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除单条通知
     */
    @DeleteMapping("/{id}")
    public Result<?> deleteById(@RequestAttribute("userId") Long userId, @PathVariable Long id) {
        try {
            // 校验通知归属
            Notification notification = notificationMapper.selectById(id);
            if (notification == null || !notification.getUserId().equals(userId)) {
                return Result.error(403, "无权删除此通知");
            }
            notificationService.deleteById(id);
            return Result.success("已删除");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}