package com.zilvzi.service;

import com.zilvzi.pojo.*;
import java.util.List;

/**
 * 圈子Service接口
 */
public interface CircleService {

    /**
     * 创建圈子
     */
    Circle createCircle(Long userId, Circle circle);

    /**
     * 获取圈子列表
     */
    List<Circle> getCircles(String tag, String keyword, Integer page, Integer size);

    /**
     * 获取圈子详情
     */
    Circle getCircleById(Long id);

    /**
     * 获取用户的圈子
     */
    List<Circle> getUserCircles(Long userId);

    /**
     * 加入圈子
     */
    void joinCircle(Long userId, Long circleId);

    /**
     * 退出圈子
     */
    void leaveCircle(Long userId, Long circleId);

    /**
     * 发布动态
     */
    Post createPost(Long userId, Long circleId, Post post);

    /**
     * 获取圈子动态
     */
    List<Post> getCirclePosts(Long circleId, Integer page, Integer size, Long userId);

    /**
     * 获取圈子成员列表
     */
    List<CircleMember> getCircleMembers(Long circleId);

    /**
     * 点赞/取消点赞
     */
    boolean toggleLike(Long userId, Long postId);

    /**
     * 发表评论
     */
    Comment addComment(Long userId, Long postId, String content);

    /**
     * 获取动态评论
     */
    List<Comment> getPostComments(Long postId);

    /**
     * 根据ID获取动态
     */
    Post getPostById(Long id);

    /**
     * 根据ID获取评论
     */
    Comment getCommentById(Long id);

    /**
     * 删除圈子（管理员）
     */
    void deleteCircle(Long circleId);

    /**
     * 删除动态（管理员）
     */
    void deletePost(Long postId);

    void deleteComment(Long commentId);

    List<Post> getAllPosts(Integer size, String keyword);

    List<Comment> getAllComments(Integer size, String keyword);
}