package com.zilvzi.mapper;

import com.zilvzi.pojo.Circle;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 圈子Mapper接口
 */
public interface CircleMapper {

    /**
     * 插入圈子
     */
    int insert(Circle circle);

    /**
     * 根据ID查询
     */
    Circle selectById(@Param("id") Long id);

    /**
     * 查询圈子列表
     */
    List<Circle> selectList(@Param("tag") String tag, @Param("keyword") String keyword, @Param("offset") Integer offset, @Param("limit") Integer limit);

    /**
     * 查询用户加入的圈子
     */
    List<Circle> selectByUserId(@Param("userId") Long userId);

    /**
     * 更新成员数
     */
    int updateMemberCount(@Param("id") Long id, @Param("count") Integer count);

    /**
     * 更新动态数
     */
    int updatePostCount(@Param("id") Long id, @Param("count") Integer count);

    int deleteById(@Param("id") Long id);

    Integer countAll();
}