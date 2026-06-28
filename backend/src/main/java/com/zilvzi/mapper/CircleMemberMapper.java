package com.zilvzi.mapper;

import com.zilvzi.pojo.CircleMember;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 圈子成员Mapper接口
 */
public interface CircleMemberMapper {

    /**
     * 插入成员
     */
    int insert(CircleMember member);

    /**
     * 查询圈子成员列表
     */
    List<CircleMember> selectByCircleId(@Param("circleId") Long circleId);

    /**
     * 查询用户是否是成员
     */
    CircleMember selectByCircleAndUser(@Param("circleId") Long circleId, @Param("userId") Long userId);

    /**
     * 更新状态
     */
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /**
     * 删除成员
     */
    int deleteById(@Param("id") Long id);

    /**
     * 统计成员数
     */
    Integer countByCircleId(@Param("circleId") Long circleId);

    /**
     * 删除圈子的所有成员
     */
    int deleteByCircleId(@Param("circleId") Long circleId);

    /**
     * 查询用户加入的所有圈子成员记录
     */
    List<CircleMember> selectByUserId(@Param("userId") Long userId);
}