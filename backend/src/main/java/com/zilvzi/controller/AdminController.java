package com.zilvzi.controller;

import com.zilvzi.pojo.Result;
import com.zilvzi.pojo.User;
import com.zilvzi.pojo.Topic;
import com.zilvzi.pojo.TopicReply;
import com.zilvzi.service.CircleService;
import com.zilvzi.service.UserService;
import com.zilvzi.mapper.UserMapper;
import com.zilvzi.mapper.CheckinMapper;
import com.zilvzi.mapper.CircleMapper;
import com.zilvzi.mapper.PostMapper;
import com.zilvzi.mapper.TopicMapper;
import com.zilvzi.mapper.TopicReplyMapper;
import com.zilvzi.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CheckinMapper checkinMapper;

    @Autowired
    private CircleMapper circleMapper;

    @Autowired
    private CircleService circleService;

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private TopicMapper topicMapper;

    @Autowired
    private TopicReplyMapper topicReplyMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/login")
    public Result<?> adminLogin(@RequestBody Map<String, String> params) {
        String username = params.get("username");
        String password = params.get("password");

        // 通过UserService验证用户
        User user = userService.loginByUsername(username, password);
        if (user == null) {
            return Result.error("用户名或密码错误");
        }

        // 检查是否是管理员
        if (user.getRole() == null || user.getRole() != 1) {
            return Result.error("无管理员权限");
        }

        // 生成管理员JWT Token
        String token = jwtUtil.generateAdminToken(user.getId(), user.getUsername(), user.getRole());
        Map<String, String> result = new HashMap<>();
        result.put("token", token);
        return Result.success("登录成功", result);
    }

    @GetMapping("/users")
    public Result<?> getUsers(@RequestParam(required = false) String keyword) {
        List<User> users;
        if (keyword != null && !keyword.isEmpty()) {
            users = userMapper.searchUsers(keyword, 0L);
        } else {
            users = userMapper.selectAll();
        }
        return Result.success(users);
    }

    @PostMapping("/users/create")
    public Result<?> createUser(@RequestBody User user) {
        userMapper.insert(user);
        return Result.success("创建成功");
    }

    @PutMapping("/users/{id}/update")
    public Result<?> updateUser(@RequestAttribute(value = "userId", required = false) Long adminId,
                                @PathVariable Long id, @RequestBody User user) {
        user.setId(id);
        // 管理员可以更新 level/exp/role
        userMapper.updateAdmin(user);
        return Result.success("更新成功");
    }

    @DeleteMapping("/users/{id}")
    public Result<?> deleteUser(@PathVariable Long id) {
        userMapper.deleteById(id);
        return Result.success("删除成功");
    }

    @GetMapping("/stats")
    public Result<?> getStats() {
        Map<String, Object> stats = new HashMap<>();

        Integer totalUsers = userMapper.countAll();
        stats.put("totalUsers", totalUsers != null ? totalUsers : 0);

        Integer todayCheckins = checkinMapper.countToday(java.time.LocalDate.now());
        stats.put("todayCheckins", todayCheckins != null ? todayCheckins : 0);

        Integer totalCircles = circleMapper.countAll();
        stats.put("totalCircles", totalCircles != null ? totalCircles : 0);

        Integer totalPosts = postMapper.countAll();
        stats.put("totalPosts", totalPosts != null ? totalPosts : 0);

        Integer totalTopics = topicMapper.countAll();
        stats.put("totalTopics", totalTopics != null ? totalTopics : 0);

        stats.put("dailyActive", 0);
        stats.put("checkinRate", 0);

        return Result.success(stats);
    }

    // === 话题管理 ===

    @GetMapping("/topics")
    public Result<?> getTopics(@RequestParam(required = false) String keyword) {
        List<Topic> topics;
        if (keyword != null && !keyword.isEmpty()) {
            topics = topicMapper.search(keyword, 0, 200);
        } else {
            topics = topicMapper.selectAll(0, 200);
        }
        return Result.success(topics);
    }

    @DeleteMapping("/topics/{id}")
    public Result<?> deleteTopic(@PathVariable Long id) {
        topicMapper.deleteById(id);
        return Result.success("删除成功");
    }

    @GetMapping("/topics/{id}/replies")
    public Result<?> getTopicReplies(@PathVariable Long id, @RequestParam(required = false) String keyword) {
        List<TopicReply> replies;
        if (keyword != null && !keyword.isEmpty()) {
            replies = topicReplyMapper.search(id, keyword);
        } else {
            replies = topicReplyMapper.selectByTopicId(id);
        }
        return Result.success(replies);
    }

    @DeleteMapping("/topics/reply/{id}")
    public Result<?> deleteTopicReply(@PathVariable Long id) {
        topicReplyMapper.deleteById(id);
        return Result.success("删除成功");
    }

    // === 圈子/动态/评论删除（仅管理员） ===

    @DeleteMapping("/circles/{id}")
    public Result<?> deleteCircle(@PathVariable Long id) {
        circleService.deleteCircle(id);
        return Result.success("删除成功");
    }

    @DeleteMapping("/posts/{id}")
    public Result<?> deletePost(@PathVariable Long id) {
        circleService.deletePost(id);
        return Result.success("删除成功");
    }

    @DeleteMapping("/comments/{id}")
    public Result<?> deleteComment(@PathVariable Long id) {
        circleService.deleteComment(id);
        return Result.success("删除成功");
    }
}
