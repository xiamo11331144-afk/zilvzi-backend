package com.zilvzi.controller;

import com.zilvzi.pojo.*;
import com.zilvzi.service.CircleService;
import com.zilvzi.utils.SensitiveWordFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 圈子Controller
 */
@RestController
@RequestMapping("/api/circle")
public class CircleController {

    @Autowired
    private CircleService circleService;

    @Autowired
    private SensitiveWordFilter sensitiveWordFilter;

    /**
     * 创建圈子
     */
    @PostMapping("/create")
    public Result<?> createCircle(@RequestAttribute("userId") Long userId, @RequestBody Circle circle) {
        try {
            Circle created = circleService.createCircle(userId, circle);
            return Result.success("创建成功", created);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取圈子列表
     */
    @GetMapping("/list")
    public Result<?> getCircleList(@RequestParam(required = false) String tag,
                                    @RequestParam(required = false) String keyword,
                                    @RequestParam(defaultValue = "1") Integer page,
                                    @RequestParam(defaultValue = "10") Integer size) {
        try {
            List<Circle> circles = circleService.getCircles(tag, keyword, page, size);
            return Result.success(circles);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取圈子详情
     */
    @GetMapping("/detail/{id}")
    public Result<?> getCircleDetail(@PathVariable Long id) {
        try {
            Circle circle = circleService.getCircleById(id);
            return Result.success(circle);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取我的圈子
     */
    @GetMapping("/my")
    public Result<?> getMyCircles(@RequestAttribute("userId") Long userId) {
        try {
            List<Circle> circles = circleService.getUserCircles(userId);
            return Result.success(circles);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 加入圈子
     */
    @PostMapping("/join/{circleId}")
    public Result<?> joinCircle(@RequestAttribute("userId") Long userId, @PathVariable Long circleId) {
        try {
            circleService.joinCircle(userId, circleId);
            return Result.success("加入成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 退出圈子
     */
    @PostMapping("/leave/{circleId}")
    public Result<?> leaveCircle(@RequestAttribute("userId") Long userId, @PathVariable Long circleId) {
        try {
            circleService.leaveCircle(userId, circleId);
            return Result.success("已退出");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 发布动态
     */
    @PostMapping("/{circleId}/post")
    public Result<?> createPost(@RequestAttribute("userId") Long userId,
                                @PathVariable Long circleId,
                                @RequestBody Post post) {
        try {
            if (sensitiveWordFilter.containsSensitive(post.getContent())) {
                String word = sensitiveWordFilter.findSensitiveWord(post.getContent());
                return Result.error("内容包含敏感词【" + word + "】，请修改后发布");
            }
            Post created = circleService.createPost(userId, circleId, post);
            return Result.success("发布成功", created);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取圈子动态
     */
    @GetMapping("/{circleId}/posts")
    public Result<?> getCirclePosts(@RequestAttribute(value = "userId", required = false) Long userId,
                                    @PathVariable Long circleId,
                                    @RequestParam(defaultValue = "1") Integer page,
                                    @RequestParam(defaultValue = "10") Integer size) {
        try {
            List<Post> posts = circleService.getCirclePosts(circleId, page, size, userId);
            return Result.success(posts);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取圈子成员列表
     */
    @GetMapping("/{circleId}/members")
    public Result<?> getCircleMembers(@PathVariable Long circleId) {
        try {
            List<CircleMember> members = circleService.getCircleMembers(circleId);
            return Result.success(members);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除圈子（仅圈子创建者或管理员）
     */
    @DeleteMapping("/delete/{circleId}")
    public Result<?> deleteCircle(@RequestAttribute("userId") Long userId, @PathVariable Long circleId) {
        try {
            Circle circle = circleService.getCircleById(circleId);
            if (circle == null) return Result.error("圈子不存在");
            if (!circle.getCreatorId().equals(userId)) return Result.error(403, "仅圈子创建者可删除");
            circleService.deleteCircle(circleId);
            return Result.success("删除成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除动态（仅帖子作者或圈子创建者）
     */
    @DeleteMapping("/post/{postId}/delete")
    public Result<?> deletePost(@RequestAttribute("userId") Long userId, @PathVariable Long postId) {
        try {
            Post post = circleService.getPostById(postId);
            if (post == null) return Result.error("动态不存在");
            Circle circle = circleService.getCircleById(post.getCircleId());
            if (!post.getUserId().equals(userId) && (circle == null || !circle.getCreatorId().equals(userId))) {
                return Result.error(403, "无权删除此动态");
            }
            circleService.deletePost(postId);
            return Result.success("删除成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取所有动态（管理员，支持模糊搜索）
     */
    @GetMapping("/all-posts")
    public Result<?> getAllPosts(@RequestParam(defaultValue = "100") Integer size,
                                 @RequestParam(required = false) String keyword) {
        try {
            List<Post> posts = circleService.getAllPosts(size, keyword);
            return Result.success(posts);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取所有评论（管理员，支持模糊搜索）
     */
    @GetMapping("/all-comments")
    public Result<?> getAllComments(@RequestParam(defaultValue = "200") Integer size,
                                    @RequestParam(required = false) String keyword) {
        try {
            List<Comment> comments = circleService.getAllComments(size, keyword);
            return Result.success(comments);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除评论（仅评论作者或圈子创建者）
     */
    @DeleteMapping("/comment/{commentId}/delete")
    public Result<?> deleteComment(@RequestAttribute("userId") Long userId, @PathVariable Long commentId) {
        try {
            Comment comment = circleService.getCommentById(commentId);
            if (comment == null) return Result.error("评论不存在");
            Post post = circleService.getPostById(comment.getPostId());
            if (post == null) return Result.error("关联动态不存在");
            Circle circle = circleService.getCircleById(post.getCircleId());
            if (!comment.getUserId().equals(userId) && (circle == null || !circle.getCreatorId().equals(userId))) {
                return Result.error(403, "无权删除此评论");
            }
            circleService.deleteComment(commentId);
            return Result.success("删除成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 点赞/取消点赞
     */
    @PostMapping("/post/{postId}/like")
    public Result<?> toggleLike(@RequestAttribute("userId") Long userId, @PathVariable Long postId) {
        try {
            boolean liked = circleService.toggleLike(userId, postId);
            return Result.success(liked ? "点赞成功" : "取消点赞");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 发表评论
     */
    @PostMapping("/post/{postId}/comment")
    public Result<?> addComment(@RequestAttribute("userId") Long userId,
                                @PathVariable Long postId,
                                @RequestBody java.util.Map<String, String> params) {
        try {
            String content = params.get("content");
            if (sensitiveWordFilter.containsSensitive(content)) {
                String word = sensitiveWordFilter.findSensitiveWord(content);
                return Result.error("评论包含敏感词【" + word + "】，请修改后发布");
            }
            Comment comment = circleService.addComment(userId, postId, content);
            return Result.success("评论成功", comment);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取评论列表
     */
    @GetMapping("/post/{postId}/comments")
    public Result<?> getComments(@PathVariable Long postId) {
        try {
            List<Comment> comments = circleService.getPostComments(postId);
            return Result.success(comments);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}