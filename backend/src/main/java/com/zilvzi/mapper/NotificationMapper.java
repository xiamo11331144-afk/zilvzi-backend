package com.zilvzi.mapper;

import com.zilvzi.pojo.Notification;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 消息通知Mapper接口
 */
public interface NotificationMapper {

    /**
     * 插入通知
     */
    int insert(Notification notification);

    /**
     * 查询用户的通知列表
     */
    List<Notification> selectByUserId(@Param("userId") Long userId, @Param("offset") Integer offset, @Param("limit") Integer limit);

    /**
     * 统计未读数量
     */
    Integer countUnread(@Param("userId") Long userId);

    /**
     * 统计指定类型的未读数量
     */
    Integer countUnreadByType(@Param("userId") Long userId, @Param("type") String type);

    /**
     * 标记已读
     */
    int markAsRead(@Param("id") Long id);

    /**
     * 标记所有已读
     */
    int markAllAsRead(@Param("userId") Long userId);

    int markAllAsReadByType(@Param("userId") Long userId, @Param("type") String type);

    /**
     * 删除用户所有通知
     */
    int deleteByUserId(@Param("userId") Long userId);

    /**
     * 删除单条通知
     */
    int deleteById(@Param("id") Long id);

    /**
     * 根据ID查询通知
     */
    Notification selectById(@Param("id") Long id);
}