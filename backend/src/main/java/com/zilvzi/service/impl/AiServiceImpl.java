package com.zilvzi.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zilvzi.service.AiService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * AI服务实现类 (DeepSeek)
 */
@Service
public class AiServiceImpl implements AiService {

    @Value("${ai.api.url}")
    private String apiUrl;

    @Value("${ai.api.key}")
    private String apiKey;

    @Value("${ai.model}")
    private String model;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String generatePlan(String goalType, String goalName, String examDate, String remark, Integer totalDays) {
        int aiDays = Math.min(totalDays, 3);
        
        String prompt = String.format("""
                制定%s学习计划，共%d天。
                目标：%s
                备注：%s
                
                只返回前%d天的JSON，格式：
                {"dailyPlan":[{"day":1,"tasks":[{"name":"任务名","type":"学习","description":"具体内容","duration":"1小时","icon":"📚","color":"#4A90D9"}]}]}
                
                要求：每天2-3个任务，任务描述简洁。
                """, goalName, totalDays, goalName, remark, aiDays);

        return callAi(prompt, 1000);
    }

    /**
     * 生成计划大纲（L1路线图）
     * 只返回阶段划分和主题，不返回具体任务
     */
    @Override
    public String generateOutline(String goalType, String goalName, String examDate, String remark, Integer totalDays, String scheduleDescription) {
        String prompt = String.format("""
                为"%s"制定学习大纲。
                考试/截止日期：%s
                用户备注：%s
                
                用户时间安排：
                %s
                
                【严格要求】：
                1. 总天数严格等于%d天，不能多也不能少
                2. 最后一个阶段的endDay必须等于%d
                3. 第一个阶段的startDay必须等于1
                4. 根据用户的每周安排，推算计划期间哪些日期可以安排任务
                5. 每周有课/无空闲时间的日期不安排任务
                
                返回JSON格式大纲：
                {
                    "phases": [
                        {
                            "phase": 1,
                            "name": "阶段名称",
                            "startDay": 1,
                            "endDay": 结束天数,
                            "theme": "阶段主题",
                            "goals": ["目标1"],
                            "topics": ["主题1"]
                        }
                    ],
                    "tips": ["建议1"]
                }
                """, goalName, examDate, remark, scheduleDescription, totalDays, totalDays);

        return callAi(prompt, 2000);
    }

    /**
     * 生成某阶段的详细任务（L2）
     * 根据阶段主题一次性生成整个阶段的任务
     */
    @Override
    public String generatePhaseTasks(String goalType, String goalName, String remark, Integer phase, Integer phaseDays, String phaseTheme, String scheduleDescription) {
        String prompt = String.format("""
                为"%s"的第%d阶段生成%d天详细任务。
                阶段主题：%s
                用户备注：%s
                
                用户时间安排：
                %s
                
                【要求】：
                1. 生成%d天的任务，day从1到%d
                2. 根据用户的每周安排，推算哪些天可以安排任务
                3. 每周有课/无空闲时间的日期，tasks数组必须为空：[]
                4. 每周无课/有空闲时间的日期，安排2-3个具体任务
                5. 任务描述要具体可执行
                
                返回JSON格式：
                {
                    "dailyPlan": [
                        {"day": 1, "tasks": [{"name": "任务名", "type": "学习", "description": "具体内容", "duration": "1小时", "icon": "📚", "color": "#4A90D9"}]},
                        {"day": 2, "tasks": []}
                    ]
                }
                """, goalName, phase, phaseDays, phaseTheme, remark, scheduleDescription, phaseDays, phaseDays);

        return callAi(prompt, 100000);
    }

    @Override
    public String chat(String message) {
        String prompt = String.format("""
                你是"自律搭子"AI助手，专门帮助用户养成好习惯、提高自律能力。
                请用友好、鼓励的语气回答以下问题，给出实用的建议。
                
                用户问题：%s
                """, message);

        return callAi(prompt);
    }

    @Override
    public String chatWithContext(String message, Map<String, Object> context) {
        StringBuilder contextStr = new StringBuilder();
        if (context != null && !context.isEmpty()) {
            contextStr.append("\n用户当前状态：\n");
            if (context.get("streakDays") != null) {
                contextStr.append("- 连续打卡天数：").append(context.get("streakDays")).append("天\n");
            }
            if (context.get("totalCheckins") != null) {
                contextStr.append("- 累计打卡：").append(context.get("totalCheckins")).append("次\n");
            }
            if (context.get("level") != null) {
                contextStr.append("- 当前等级：").append(context.get("level")).append("\n");
            }
            if (context.get("plans") != null) {
                contextStr.append("- 当前计划：").append(context.get("plans")).append("\n");
            }
            if (context.get("todayCompleted") != null) {
                contextStr.append("- 今日已完成：").append(context.get("todayCompleted")).append("项\n");
            }
            if (context.get("todayTotal") != null) {
                contextStr.append("- 今日总任务：").append(context.get("todayTotal")).append("项\n");
            }
        }

        String prompt = String.format("""
                你是"自律搭子"AI助手，专门帮助用户养成好习惯、提高自律能力。
                请用友好、鼓励的语气回答以下问题，给出实用的建议。
                %s
                用户问题：%s
                
                注意：回答要简洁明了，控制在200字以内。
                """, contextStr.toString(), message);

        return callAi(prompt);
    }

    @Override
    public String chatWithHistory(String message, Map<String, Object> context,
                                   java.util.List<com.zilvzi.pojo.AiChatHistory> history) {
        // 构建用户上下文描述
        StringBuilder contextStr = new StringBuilder();
        if (context != null && !context.isEmpty()) {
            contextStr.append("\n用户当前状态：");
            if (context.get("streakDays") != null) {
                contextStr.append(" 连续打卡").append(context.get("streakDays")).append("天;");
            }
            if (context.get("totalCheckins") != null) {
                contextStr.append(" 累计打卡").append(context.get("totalCheckins")).append("次;");
            }
            if (context.get("level") != null) {
                contextStr.append(" 等级").append(context.get("level")).append(";");
            }
            if (context.get("plans") != null) {
                contextStr.append(" 当前计划：").append(context.get("plans")).append(";");
            }
        }

        String systemPrompt = "你是\"自律搭子\"AI助手，专门帮助用户养成好习惯、提高自律能力。"
                + "请用友好、鼓励的语气回答，给出实用的建议。回答控制在200字以内。"
                + contextStr.toString();

        // 构建多轮对话 messages 数组
        java.util.List<Map<String, String>> messages = new java.util.ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));

        // 加入历史对话（最近10轮 = 20条）
        if (history != null) {
            int start = Math.max(0, history.size() - 20);
            for (int i = start; i < history.size(); i++) {
                com.zilvzi.pojo.AiChatHistory h = history.get(i);
                messages.add(Map.of("role", h.getRole(), "content", h.getContent()));
            }
        }

        // 加入当前用户消息
        messages.add(Map.of("role", "user", "content", message));

        return callAiWithMessages(messages, 2000);
    }

    @Override
    public String getAdvice(Map<String, Object> checkinData) {
        String prompt = String.format("""
                基于用户的打卡数据，给出个性化的习惯养成建议：
                
                打卡数据：%s
                
                请分析用户的情况，给出3-5条具体可行的建议。
                """, checkinData.toString());

        return callAi(prompt, 2000);
    }

    private String callAi(String prompt) {
        return callAi(prompt, 2000);
    }

    private String callAi(String prompt, int maxTokens) {
        try {
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "model", model,
                    "messages", new Object[]{
                            Map.of("role", "system", "content", "你是一个专业的学习计划和习惯养成助手。请简洁回答，只返回JSON。"),
                            Map.of("role", "user", "content", prompt)
                    },
                    "temperature", 0.7,
                    "max_tokens", maxTokens
            ));

            System.out.println("[AI] 请求URL: " + apiUrl);
            System.out.println("[AI] 请求体: " + requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("[AI] 响应状态: " + response.statusCode());
            System.out.println("[AI] 响应体: " + response.body());

            JsonNode jsonNode = objectMapper.readTree(response.body());
            JsonNode choices = jsonNode.get("choices");

            if (choices != null && choices.size() > 0) {
                String rawContent = choices.get(0).get("message").get("content").asText();
                return extractResponseContent(rawContent);
            }

            return "抱歉，AI暂时无法生成回答。";
        } catch (Exception e) {
            System.out.println("[AI] 调用异常: " + e.getMessage());
            e.printStackTrace();
            return "AI服务调用失败：" + e.getMessage();
        }
    }

    /**
     * 支持多轮对话的 AI 调用（传入完整 messages 数组）
     */
    private String callAiWithMessages(java.util.List<Map<String, String>> messages, int maxTokens) {
        try {
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "model", model,
                    "messages", messages,
                    "temperature", 0.7,
                    "max_tokens", maxTokens
            ));

            System.out.println("[AI] 多轮对话请求数: " + messages.size() + " 条消息");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            JsonNode jsonNode = objectMapper.readTree(response.body());
            JsonNode choices = jsonNode.get("choices");

            if (choices != null && choices.size() > 0) {
                String rawContent = choices.get(0).get("message").get("content").asText();
                return extractResponseContent(rawContent);
            }

            return "抱歉，AI暂时无法生成回答。";
        } catch (Exception e) {
            System.out.println("[AI] 多轮对话调用异常: " + e.getMessage());
            return "AI服务调用失败：" + e.getMessage();
        }
    }

    /**
     * 从 AI 返回内容中提取实际文本
     * AI 可能返回 {"response": "..."} 格式的 JSON，需要提取 response 字段
     */
    private String extractResponseContent(String rawContent) {
        if (rawContent == null || rawContent.trim().isEmpty()) {
            return "抱歉，AI返回内容为空。";
        }
        String trimmed = rawContent.trim();
        // 尝试解析 JSON 格式的响应
        if (trimmed.startsWith("{")) {
            try {
                JsonNode node = objectMapper.readTree(trimmed);
                // 优先取 response 字段
                if (node.has("response")) {
                    return node.get("response").asText();
                }
                // 其次取 content 字段
                if (node.has("content")) {
                    return node.get("content").asText();
                }
                // 取 message 字段
                if (node.has("message")) {
                    return node.get("message").asText();
                }
                // 取 reply 字段
                if (node.has("reply")) {
                    return node.get("reply").asText();
                }
                // 如果有 data 字段
                if (node.has("data")) {
                    JsonNode data = node.get("data");
                    if (data.isTextual()) {
                        return data.asText();
                    }
                    return data.toString();
                }
            } catch (Exception ignored) {
                // JSON 解析失败，返回原始内容
            }
        }
        // 去除可能的 markdown 代码块包裹
        if (trimmed.startsWith("```json") && trimmed.endsWith("```")) {
            return trimmed.substring(7, trimmed.length() - 3).trim();
        }
        if (trimmed.startsWith("```") && trimmed.endsWith("```")) {
            return trimmed.substring(3, trimmed.length() - 3).trim();
        }
        return trimmed;
    }
}