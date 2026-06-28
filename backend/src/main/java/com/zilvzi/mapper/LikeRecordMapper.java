package com.zilvzi.mapper;

import com.zilvzi.pojo.LikeRecord;
import org.apache.ibatis.annotations.Param;

/**
 * 点赞记录Mapper接口
 */
public interface LikeRecordMapper {

    /**
     * 插入点赞
     */
    int insert(LikeRecord likeRecord);

    /**
     * 查询用户是否点赞
     */
    LikeRecord selectByUserAndPost(@Param("userId") Long userId, @Param("postId") Long postId);

    /**
     * 删除点赞
     */
    int deleteById(@Param("id") Long id);

    /**
     * 删除某帖子的所有点赞
     */
    int deleteByPostId(@Param("postId") Long postId);

    /**
     * 统计点赞数
     */
    Integer countByPostId(@Param("postId") Long postId);
}