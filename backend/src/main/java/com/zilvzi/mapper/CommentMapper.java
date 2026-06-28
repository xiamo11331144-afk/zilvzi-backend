package com.zilvzi.mapper;

import com.zilvzi.pojo.Comment;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 评论Mapper接口
 */
public interface CommentMapper {

    /**
     * 根据ID查询评论
     */
    Comment selectById(@Param("id") Long id);

    /**
     * 插入评论
     */
    int insert(Comment comment);

    /**
     * 查询动态的评论列表
     */
    List<Comment> selectByPostId(@Param("postId") Long postId);

    /**
     * 删除评论
     */
    int deleteById(@Param("id") Long id);

    /**
     * 删除某帖子的所有评论
     */
    int deleteByPostId(@Param("postId") Long postId);

    /**
     * 统计评论数
     */
    Integer countByPostId(@Param("postId") Long postId);

    List<Comment> selectAll(@Param("limit") Integer limit, @Param("keyword") String keyword);
}