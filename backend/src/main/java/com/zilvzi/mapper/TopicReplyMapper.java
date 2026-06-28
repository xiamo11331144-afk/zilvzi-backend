package com.zilvzi.mapper;

import com.zilvzi.pojo.TopicReply;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface TopicReplyMapper {
    int insert(TopicReply reply);
    List<TopicReply> selectByTopicId(@Param("topicId") Long topicId);
    int countByTopicId(@Param("topicId") Long topicId);
    int deleteById(@Param("id") Long id);
    List<TopicReply> search(@Param("topicId") Long topicId, @Param("keyword") String keyword);
}
