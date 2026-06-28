package com.zilvzi.mapper;

import com.zilvzi.pojo.Achievement;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 成就Mapper接口
 */
public interface AchievementMapper {

    /**
     * 查询用户的成就列表
     */
    List<Achievement> selectByUserId(@Param("userId") Long userId);

    /**
     * 查询用户是否已解锁某成就
     */
    Achievement selectByUserIdAndName(@Param("userId") Long userId, @Param("medalName") String medalName);

    /**
     * 插入成就（INSERT IGNORE 防重复）
     */
    int insert(Achievement achievement);

    /**
     * 统计用户是否已拥有某勋章
     */
    int countByUserIdAndName(@Param("userId") Long userId, @Param("medalName") String medalName);
}