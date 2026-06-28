package com.zilvzi.service;

import java.util.Map;

/**
 * AI服务接口
 */
public interface AiService {

    /**
     * 生成学习计划（完整）
     */
    String generatePlan(String goalType, String goalName, String examDate, String remark, Integer totalDays);

    /**
     * 生成计划大纲（L1路线图）
     */
    String generateOutline(String goalType, String goalName, String examDate, String remark, Integer totalDays, String scheduleDescription);

    /**
     * 生成某阶段的详细任务（L2）
     */
    String generatePhaseTasks(String goalType, String goalName, String remark, Integer phase, Integer phaseDays, String phaseTheme, String scheduleDescription);

    /**
     * 智能对话
     */
    String chat(String message);

    /**
     * 智能对话（带用户上下文）
     */
    String chatWithContext(String message, Map<String, Object> context);

    /**
     * 智能对话（带历史记录 + 用户上下文）
     */
    String chatWithHistory(String message, Map<String, Object> context, java.util.List<com.zilvzi.pojo.AiChatHistory> history);

    /**
     * 个性化建议
     */
    String getAdvice(Map<String, Object> checkinData);
}