package com.zilvzi.service.impl;

import com.zilvzi.mapper.TopicMapper;
import com.zilvzi.mapper.TopicReplyMapper;
import com.zilvzi.mapper.UserMapper;
import com.zilvzi.pojo.Topic;
import com.zilvzi.pojo.TopicReply;
import com.zilvzi.pojo.User;
import com.zilvzi.service.NotificationService;
import com.zilvzi.service.TopicService;
import com.zilvzi.utils.SensitiveWordFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class TopicServiceImpl implements TopicService {

    @Autowired
    private TopicMapper topicMapper;

    @Autowired
    private TopicReplyMapper topicReplyMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private SensitiveWordFilter sensitiveWordFilter;

    @Override
    public Topic createTopic(Long userId, Long circleId, String title, String content) {
        if (sensitiveWordFilter.containsSensitive(title)) {
            throw new RuntimeException("标题包含敏感词");
        }
        if (content != null && sensitiveWordFilter.containsSensitive(content)) {
            throw new RuntimeException("内容包含敏感词");
        }
        Topic topic = new Topic();
        topic.setUserId(userId);
        topic.setCircleId(circleId);
        topic.setTitle(title);
        topic.setContent(content);
        topicMapper.insert(topic);
        return topic;
    }

    @Override
    public List<Topic> getTopicsByCircle(Long circleId, Integer page, Integer size) {
        Integer offset = (page - 1) * size;
        return topicMapper.selectByCircleId(circleId, offset, size);
    }

    @Override
    public Topic getTopicDetail(Long id) {
        return topicMapper.selectById(id);
    }

    @Override
    public Topic getTopicById(Long id) {
        return topicMapper.selectById(id);
    }

    @Override
    public TopicReply addReply(Long userId, Long topicId, String content) {
        if (sensitiveWordFilter.containsSensitive(content)) {
            String word = sensitiveWordFilter.findSensitiveWord(content);
            throw new RuntimeException("内容包含敏感词【" + word + "】");
        }
        TopicReply reply = new TopicReply();
        reply.setUserId(userId);
        reply.setTopicId(topicId);
        reply.setContent(content);
        topicReplyMapper.insert(reply);

        int count = topicReplyMapper.countByTopicId(topicId);
        topicMapper.updateReplyCount(topicId, count);

        // 通知话题作者
        Topic topic = topicMapper.selectById(topicId);
        if (topic != null && !topic.getUserId().equals(userId)) {
            User user = userMapper.selectById(userId);
            String displayContent = content.length() > 20 ? content.substring(0, 20) + "..." : content;
            notificationService.sendNotification(
                topic.getUserId(), userId, "topic_reply",
                "话题回复", user.getNickname() + " 回复了你的话题: " + displayContent,
                "topic", topicId
            );
        }

        return reply;
    }

    @Override
    public List<TopicReply> getReplies(Long topicId) {
        return topicReplyMapper.selectByTopicId(topicId);
    }

    @Override
    public void deleteTopic(Long id) {
        topicMapper.deleteById(id);
    }

    @Override
    public void updateTopic(Long id, Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            throw new IllegalArgumentException("至少提供一个可更新字段");
        }
        for (String key : params.keySet()) {
            if (!List.of("title", "content", "isPinned").contains(key)) {
                throw new IllegalArgumentException("包含不支持的更新字段");
            }
        }

        String title = null;
        if (params.containsKey("title")) {
            Object value = params.get("title");
            if (!(value instanceof String text) || text.trim().isEmpty()
                    || text.trim().codePointCount(0, text.trim().length()) > 200) {
                throw new IllegalArgumentException("标题不能为空且不能超过200个字符");
            }
            title = text.trim();
            if (sensitiveWordFilter.containsSensitive(title)) {
                throw new IllegalArgumentException("标题包含敏感词");
            }
        }

        String content = null;
        if (params.containsKey("content")) {
            Object value = params.get("content");
            if (!(value instanceof String text)) {
                throw new IllegalArgumentException("内容格式不正确");
            }
            content = text;
            if (sensitiveWordFilter.containsSensitive(content)) {
                throw new IllegalArgumentException("内容包含敏感词");
            }
        }

        Integer isPinned = null;
        if (params.containsKey("isPinned")) {
            Object value = params.get("isPinned");
            if (!(value instanceof Integer flag) || (flag != 0 && flag != 1)) {
                throw new IllegalArgumentException("置顶状态只能为0或1");
            }
            isPinned = flag;
        }

        topicMapper.update(id, title, content, isPinned);
    }

    @Override
    public List<Topic> getAllTopics(Integer page, Integer size) {
        Integer offset = (page - 1) * size;
        return topicMapper.selectAll(offset, size);
    }
}
