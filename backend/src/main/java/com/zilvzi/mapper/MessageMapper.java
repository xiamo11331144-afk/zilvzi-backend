package com.zilvzi.mapper;

import com.zilvzi.pojo.Message;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

public interface MessageMapper {
    int insert(Message message);
    List<Message> selectByUserId(@Param("userId") Long userId, @Param("offset") Integer offset, @Param("limit") Integer limit);
    List<Message> selectConversation(@Param("userId") Long userId, @Param("partnerId") Long partnerId, @Param("offset") Integer offset, @Param("limit") Integer limit);
    Integer countUnread(@Param("userId") Long userId);
    int markAsRead(@Param("id") Long id);
    int markConversationAsRead(@Param("userId") Long userId, @Param("fromUserId") Long fromUserId);
    int markMessagesAsRead(@Param("userId") Long userId, @Param("fromUserId") Long fromUserId,
                           @Param("messageIds") List<Long> messageIds);
    Message selectById(@Param("id") Long id);
    int updateContent(Message message);
    List<Map> selectConversations(@Param("userId") Long userId, @Param("offset") Integer offset, @Param("limit") Integer limit);
}