package com.zilvzi.mapper;

import com.zilvzi.pojo.Post;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 动态Mapper接口
 */
public interface PostMapper {

    /**
     * 插入动态
     */
    int insert(Post post);

    /**
     * 根据ID查询
     */
    Post selectById(@Param("id") Long id);

    /**
     * 查询圈子动态
     */
    List<Post> selectByCircleId(@Param("circleId") Long circleId, @Param("offset") Integer offset, @Param("limit") Integer limit);

    /**
     * 统计圈子动态数
     */
    Integer countByCircleId(@Param("circleId") Long circleId);

    /**
     * 更新点赞数
     */
    int updateLikeCount(@Param("id") Long id, @Param("count") Integer count);

    /**
     * 更新评论数
     */
    int updateCommentCount(@Param("id") Long id, @Param("count") Integer count);

    int deleteById(@Param("id") Long id);

    List<Post> selectAll(@Param("limit") Integer limit, @Param("keyword") String keyword);

    Integer countAll();
}