package com.zilvzi.controller;

import com.zilvzi.mapper.AiChatHistoryMapper;
import com.zilvzi.pojo.AiChatHistory;
import com.zilvzi.pojo.Result;
import com.zilvzi.service.AiService;
import com.zilvzi.utils.SensitiveWordFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI控制器
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    @Autowired
    private AiService aiService;

    @Autowired
    private SensitiveWordFilter sensitiveWordFilter;

    @Autowired
    private AiChatHistoryMapper aiChatHistoryMapper;

    /**
     * 智能对话（支持传入用户上下文，自动保存对话历史）
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/chat")
    public Result<?> chat(@RequestAttribute("userId") Long userId, @RequestBody Map<String, Object> params) {
        try {
            String message = (String) params.get("message");
            if (sensitiveWordFilter.containsSensitive(message)) {
                String word = sensitiveWordFilter.findSensitiveWord(message);
                return Result.error("消息包含敏感词【" + word + "】，请修改后发送");
            }

            // 保存用户消息
            AiChatHistory userRecord = new AiChatHistory();
            userRecord.setUserId(userId);
            userRecord.setRole("user");
            userRecord.setContent(message);
            userRecord.setSessionType("chat");
            aiChatHistoryMapper.insert(userRecord);

            // 获取历史对话作为上下文（最近10轮）
            List<AiChatHistory> history = aiChatHistoryMapper.selectContextByUserId(userId, "chat", 20);

            Map<String, Object> context = (Map<String, Object>) params.get("context");
            String reply = aiService.chatWithHistory(message, context, history);

            // 保存AI回复
            AiChatHistory aiRecord = new AiChatHistory();
            aiRecord.setUserId(userId);
            aiRecord.setRole("assistant");
            aiRecord.setContent(reply);
            aiRecord.setSessionType("chat");
            aiChatHistoryMapper.insert(aiRecord);

            return Result.success(reply);
        } catch (Exception e) {
            return Result.error("对话失败：" + e.getMessage());
        }
    }

    /**
     * 获取对话历史
     */
    @GetMapping("/history")
    public Result<?> getHistory(@RequestAttribute("userId") Long userId,
                                @RequestParam(defaultValue = "50") Integer limit) {
        try {
            List<AiChatHistory> history = aiChatHistoryMapper.selectRecentByUserId(userId, "chat", limit);
            // 倒序转正序（前端需要时间正序）
            java.util.Collections.reverse(history);
            return Result.success(history);
        } catch (Exception e) {
            return Result.error("获取历史失败：" + e.getMessage());
        }
    }

    /**
     * 清除对话历史
     */
    @DeleteMapping("/history")
    public Result<?> clearHistory(@RequestAttribute("userId") Long userId) {
        try {
            aiChatHistoryMapper.deleteByUserIdAndType(userId, "chat");
            return Result.success("对话历史已清除");
        } catch (Exception e) {
            return Result.error("清除失败：" + e.getMessage());
        }
    }

    /**
     * 个性化建议
     */
    @PostMapping("/advice")
    public Result<?> getAdvice(@RequestBody Map<String, Object> checkinData) {
        try {
            String advice = aiService.getAdvice(checkinData);
            return Result.success(advice);
        } catch (Exception e) {
            return Result.error("获取建议失败：" + e.getMessage());
        }
    }
}