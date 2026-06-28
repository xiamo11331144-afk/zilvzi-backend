package com.zilvzi.service.impl;

import com.zilvzi.mapper.*;
import com.zilvzi.pojo.*;
import com.zilvzi.service.CircleService;
import com.zilvzi.service.NotificationService;
import com.zilvzi.service.AchievementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 圈子Service实现类
 */
@Service
public class CircleServiceImpl implements CircleService {

    @Autowired
    private CircleMapper circleMapper;

    @Autowired
    private CircleMemberMapper circleMemberMapper;

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private LikeRecordMapper likeRecordMapper;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private AchievementService achievementService;

    @Override
    @Transactional
    public Circle createCircle(Long userId, Circle circle) {
        if (circle.getName() == null || circle.getName().trim().isEmpty()) {
            throw new RuntimeException("圈子名称不能为空");
        }
        circle.setCreatorId(userId);
        circle.setMemberCount(1);
        circle.setPostCount(0);
        circleMapper.insert(circle);

        // 创建者自动成为成员
        CircleMember member = new CircleMember();
        member.setCircleId(circle.getId());
        member.setUserId(userId);
        member.setRole(1); // 管理员
        member.setStatus(1); // 已加入
        circleMemberMapper.insert(member);

        return circle;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Circle> getCircles(String tag, String keyword, Integer page, Integer size) {
        Integer offset = (page - 1) * size;
        List<Circle> circles = circleMapper.selectList(tag, keyword, offset, size);
        fillCreatorInfo(circles);
        return circles;
    }

    @Override
    @Transactional(readOnly = true)
    public Circle getCircleById(Long id) {
        Circle circle = circleMapper.selectById(id);
        if (circle != null) {
            fillCreatorInfo(circle);
        }
        return circle;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Circle> getUserCircles(Long userId) {
        List<Circle> circles = circleMapper.selectByUserId(userId);
        fillCreatorInfo(circles);
        return circles;
    }

    /** 填充圈子创建者的昵称和头像 */
    private void fillCreatorInfo(Circle circle) {
        if (circle != null && circle.getCreatorId() != null) {
            User creator = userMapper.selectById(circle.getCreatorId());
            if (creator != null) {
                circle.setCreatorNickname(creator.getNickname());
                circle.setCreatorAvatar(creator.getAvatar());
            }
        }
    }

    private void fillCreatorInfo(List<Circle> circles) {
        if (circles != null) {
            for (Circle c : circles) {
                fillCreatorInfo(c);
            }
        }
    }

    @Override
    @Transactional
    public void joinCircle(Long userId, Long circleId) {
        // 校验圈子存在
        Circle circle = circleMapper.selectById(circleId);
        if (circle == null) {
            throw new RuntimeException("圈子不存在");
        }

        // 检查是否已是成员
        CircleMember existing = circleMemberMapper.selectByCircleAndUser(circleId, userId);
        if (existing != null) {
            throw new RuntimeException("已经是圈子成员");
        }

        CircleMember member = new CircleMember();
        member.setCircleId(circleId);
        member.setUserId(userId);
        member.setRole(0);
        member.setStatus(1);
        circleMemberMapper.insert(member);

        // 更新成员数
        circleMapper.updateMemberCount(circleId, circleMemberMapper.countByCircleId(circleId));

        // 加入圈子后检查社交勋章
        achievementService.checkSocialAchievements(userId);
    }

    @Override
    @Transactional
    public void leaveCircle(Long userId, Long circleId) {
        CircleMember member = circleMemberMapper.selectByCircleAndUser(circleId, userId);
        if (member == null) {
            throw new RuntimeException("不是圈子成员");
        }

        // 阻止创建者退出
        Circle circle = circleMapper.selectById(circleId);
        if (circle != null && circle.getCreatorId().equals(userId)) {
            throw new RuntimeException("圈子创建者不能退出，请先转让或删除圈子");
        }

        circleMemberMapper.deleteById(member.getId());
        circleMapper.updateMemberCount(circleId, circleMemberMapper.countByCircleId(circleId));
    }

    @Override
    @Transactional
    public Post createPost(Long userId, Long circleId, Post post) {
        // 校验圈子存在
        Circle circle = circleMapper.selectById(circleId);
        if (circle == null) {
            throw new RuntimeException("圈子不存在");
        }

        // 校验用户是圈子成员
        CircleMember member = circleMemberMapper.selectByCircleAndUser(circleId, userId);
        if (member == null) {
            throw new RuntimeException("请先加入圈子再发布动态");
        }

        post.setUserId(userId);
        post.setCircleId(circleId);
        post.setLikeCount(0);
        post.setCommentCount(0);
        postMapper.insert(post);

        // 更新动态数
        circleMapper.updatePostCount(circleId, postMapper.countByCircleId(circleId));

        return post;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Post> getCirclePosts(Long circleId, Integer page, Integer size, Long userId) {
        Integer offset = (page - 1) * size;
        List<Post> posts = postMapper.selectByCircleId(circleId, offset, size);
        // 标记当前用户是否点赞
        for (Post post : posts) {
            if (userId != null) {
                LikeRecord like = likeRecordMapper.selectByUserAndPost(userId, post.getId());
                post.setIsLiked(like != null);
            } else {
                post.setIsLiked(false);
            }
        }
        return posts;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CircleMember> getCircleMembers(Long circleId) {
        return circleMemberMapper.selectByCircleId(circleId);
    }

    @Override
    @Transactional
    public boolean toggleLike(Long userId, Long postId) {
        LikeRecord existing = likeRecordMapper.selectByUserAndPost(userId, postId);
        if (existing != null) {
            // 取消点赞
            likeRecordMapper.deleteById(existing.getId());
            postMapper.updateLikeCount(postId, likeRecordMapper.countByPostId(postId));
            return false;
        } else {
            // 点赞
            LikeRecord like = new LikeRecord();
            like.setUserId(userId);
            like.setPostId(postId);
            try {
                likeRecordMapper.insert(like);
            } catch (Exception e) {
                // 并发重复点赞，忽略
                return true;
            }
            postMapper.updateLikeCount(postId, likeRecordMapper.countByPostId(postId));

            // 通知帖子作者
            Post post = postMapper.selectById(postId);
            if (post != null && !post.getUserId().equals(userId)) {
                User user = userMapper.selectById(userId);
                if (user != null) {
                    notificationService.sendNotification(
                        post.getUserId(), userId, "like",
                        "点赞", user.getNickname() + " 赞了你的帖子",
                        "post", postId
                    );
                }
            }

            return true;
        }
    }

    @Override
    @Transactional
    public Comment addComment(Long userId, Long postId, String content) {
        // 校验帖子存在
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new RuntimeException("动态不存在");
        }

        Comment comment = new Comment();
        comment.setUserId(userId);
        comment.setPostId(postId);
        comment.setContent(content);
        commentMapper.insert(comment);

        // 更新评论数
        postMapper.updateCommentCount(postId, commentMapper.countByPostId(postId));

        // 发送通知给帖子作者
        if (!post.getUserId().equals(userId)) {
            User user = userMapper.selectById(userId);
            if (user != null) {
                String displayContent = content.length() > 20 ? content.substring(0, 20) + "..." : content;
                notificationService.sendNotification(
                    post.getUserId(), userId, "comment",
                    "评论", user.getNickname() + " 评论了你的帖子: " + displayContent,
                    "post", postId
                );
            }
        }

        return comment;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Comment> getPostComments(Long postId) {
        return commentMapper.selectByPostId(postId);
    }

    @Override
    @Transactional(readOnly = true)
    public Post getPostById(Long id) {
        return postMapper.selectById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Comment getCommentById(Long id) {
        return commentMapper.selectById(id);
    }

    @Override
    @Transactional
    public void deleteCircle(Long circleId) {
        // 级联删除：先删帖子评论、点赞、帖子，再删成员，最后删圈子
        List<Post> posts = postMapper.selectByCircleId(circleId, 0, 10000);
        for (Post post : posts) {
            commentMapper.deleteByPostId(post.getId());
            likeRecordMapper.deleteByPostId(post.getId());
            postMapper.deleteById(post.getId());
        }
        circleMemberMapper.deleteByCircleId(circleId);
        circleMapper.deleteById(circleId);
    }

    @Override
    @Transactional
    public void deletePost(Long postId) {
        // 获取帖子所属圈子，用于后续更新计数
        Post post = postMapper.selectById(postId);
        Long circleId = post != null ? post.getCircleId() : null;

        // 级联删除：先删评论和点赞，再删帖子
        commentMapper.deleteByPostId(postId);
        likeRecordMapper.deleteByPostId(postId);
        postMapper.deleteById(postId);

        // 更新圈子动态计数
        if (circleId != null) {
            circleMapper.updatePostCount(circleId, postMapper.countByCircleId(circleId));
        }
    }

    @Override
    @Transactional
    public void deleteComment(Long commentId) {
        // 获取评论所属帖子，用于后续更新计数
        Comment comment = commentMapper.selectById(commentId);
        Long postId = comment != null ? comment.getPostId() : null;

        commentMapper.deleteById(commentId);

        // 更新帖子评论计数
        if (postId != null) {
            postMapper.updateCommentCount(postId, commentMapper.countByPostId(postId));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Post> getAllPosts(Integer size, String keyword) {
        return postMapper.selectAll(size, keyword);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Comment> getAllComments(Integer size, String keyword) {
        return commentMapper.selectAll(size, keyword);
    }
}