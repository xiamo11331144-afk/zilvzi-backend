package com.zilvzi.mapper;

import com.zilvzi.pojo.Topic;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface TopicMapper {
    int insert(Topic topic);
    Topic selectById(@Param("id") Long id);
    List<Topic> selectByCircleId(@Param("circleId") Long circleId, @Param("offset") Integer offset, @Param("limit") Integer limit);
    List<Topic> selectAll(@Param("offset") Integer offset, @Param("limit") Integer limit);
    int updateReplyCount(@Param("id") Long id, @Param("count") Integer count);
    int deleteById(@Param("id") Long id);
    Integer countAll();
    List<Topic> search(@Param("keyword") String keyword, @Param("offset") Integer offset, @Param("limit") Integer limit);
    int update(@Param("id") Long id, @Param("title") String title, @Param("content") String content, @Param("isPinned") Integer isPinned);
}
