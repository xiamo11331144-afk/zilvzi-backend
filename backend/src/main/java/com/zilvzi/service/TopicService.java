package com.zilvzi.service;

import com.zilvzi.pojo.Topic;
import com.zilvzi.pojo.TopicReply;
import java.util.List;
import java.util.Map;

public interface TopicService {
    Topic createTopic(Long userId, Long circleId, String title, String content);
    List<Topic> getTopicsByCircle(Long circleId, Integer page, Integer size);
    Topic getTopicDetail(Long id);
    Topic getTopicById(Long id);
    TopicReply addReply(Long userId, Long topicId, String content);
    List<TopicReply> getReplies(Long topicId);
    void deleteTopic(Long id);
    void updateTopic(Long id, Map<String, Object> params);
    List<Topic> getAllTopics(Integer page, Integer size);
}
