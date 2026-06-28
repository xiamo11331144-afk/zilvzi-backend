package com.zilvzi.controller;

import com.zilvzi.mapper.MessageMapper;
import com.zilvzi.mapper.PartnerMapper;
import com.zilvzi.mapper.UserMapper;
import com.zilvzi.pojo.Message;
import com.zilvzi.pojo.Partner;
import com.zilvzi.pojo.Result;
import com.zilvzi.service.NotificationService;
import com.zilvzi.utils.SensitiveWordFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/message")
public class MessageController {

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private PartnerMapper partnerMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private SensitiveWordFilter sensitiveWordFilter;

    @PostMapping("/send")
    public Result<?> sendMessage(@RequestAttribute("userId") Long userId, @RequestBody Map<String, String> params) {
        try {
            Long toUserId = Long.valueOf(params.get("toUserId"));
            String content = params.get("content");

            if (userId.equals(toUserId)) {
                return Result.error("不能给自己发送消息");
            }

            if (content == null || content.trim().isEmpty()) {
                return Result.error("消息内容不能为空");
            }

            if (sensitiveWordFilter.containsSensitive(content)) {
                String word = sensitiveWordFilter.findSensitiveWord(content);
                return Result.error("消息包含敏感词【" + word + "】，请修改后发送");
            }

            Message message = new Message();
            message.setFromUserId(userId);
            message.setToUserId(toUserId);
            message.setContent(content.trim());
            messageMapper.insert(message);

            // 发送通知
            notificationService.sendNotification(
                toUserId, userId, "private_message",
                "私信", "给你发来一条消息",
                null, null
            );

            return Result.success("发送成功", message);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取对话列表（按对方用户分组，每组返回最新一条）
     */
    @GetMapping("/conversations")
    public Result<?> getConversations(@RequestAttribute("userId") Long userId,
                                     @RequestParam(defaultValue = "1") Integer page,
                                     @RequestParam(defaultValue = "20") Integer size) {
        try {
            Integer offset = (page - 1) * size;
            List<Map> conversations = messageMapper.selectConversations(userId, offset, size);
            return Result.success(conversations);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取原始消息列表（保留兼容）
     */
    @GetMapping("/list")
    public Result<?> getUserMessages(@RequestAttribute("userId") Long userId,
                                     @RequestParam(defaultValue = "1") Integer page,
                                     @RequestParam(defaultValue = "20") Integer size) {
        try {
            Integer offset = (page - 1) * size;
            List<Message> messages = messageMapper.selectByUserId(userId, offset, size);
            return Result.success(messages);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/conversation/{partnerId}")
    public Result<?> getConversation(@RequestAttribute("userId") Long userId,
                                     @PathVariable Long partnerId,
                                     @RequestParam(defaultValue = "1") Integer page,
                                     @RequestParam(defaultValue = "50") Integer size) {
        try {
            Integer offset = (page - 1) * size;
            List<Message> messages = messageMapper.selectConversation(userId, partnerId, offset, size);

            // 自动标记该对话的所有未读消息为已读
            messageMapper.markConversationAsRead(userId, partnerId);

            return Result.success(messages);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/read/{id}")
    public Result<?> markAsRead(@RequestAttribute("userId") Long userId, @PathVariable Long id) {
        try {
            Message message = messageMapper.selectById(id);
            if (message == null || !message.getToUserId().equals(userId)) {
                return Result.error(403, "无权操作此消息");
            }
            messageMapper.markAsRead(id);
            return Result.success("已标记已读");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 批量标记某用户发来的消息为已读
     */
    @PutMapping("/read/from/{fromUserId}")
    public Result<?> markAllFromUser(@RequestAttribute("userId") Long userId, @PathVariable Long fromUserId) {
        try {
            messageMapper.markConversationAsRead(userId, fromUserId);
            return Result.success("已标记已读");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/unread/count")
    public Result<?> getUnreadCount(@RequestAttribute("userId") Long userId) {
        try {
            Integer count = messageMapper.countUnread(userId);
            return Result.success(count != null ? count : 0);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}