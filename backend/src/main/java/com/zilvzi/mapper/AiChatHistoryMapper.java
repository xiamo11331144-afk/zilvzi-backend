package com.zilvzi.mapper;

import com.zilvzi.pojo.AiChatHistory;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * AI对话历史Mapper接口
 */
public interface AiChatHistoryMapper {

    /**
     * 插入对话记录
     */
    int insert(AiChatHistory record);

    /**
     * 查询用户最近的对话历史（按时间倒序）
     */
    List<AiChatHistory> selectRecentByUserId(@Param("userId") Long userId,
                                              @Param("sessionType") String sessionType,
                                              @Param("limit") Integer limit);

    /**
     * 查询用户对话历史（用于构建上下文，按时间正序）
     */
    List<AiChatHistory> selectContextByUserId(@Param("userId") Long userId,
                                               @Param("sessionType") String sessionType,
                                               @Param("limit") Integer limit);

    /**
     * 删除用户所有对话历史
     */
    int deleteByUserId(@Param("userId") Long userId);

    /**
     * 删除用户指定类型的对话历史
     */
    int deleteByUserIdAndType(@Param("userId") Long userId, @Param("sessionType") String sessionType);

    /**
     * 统计用户对话数量
     */
    Integer countByUserId(@Param("userId") Long userId);
}