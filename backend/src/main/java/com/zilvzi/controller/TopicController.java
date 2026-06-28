package com.zilvzi.controller;

import com.zilvzi.pojo.Result;
import com.zilvzi.pojo.Topic;
import com.zilvzi.pojo.TopicReply;
import com.zilvzi.service.TopicService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/topic")
public class TopicController {

    @Autowired
    private TopicService topicService;

    @PostMapping("/create")
    public Result<?> create(@RequestAttribute("userId") Long userId, @RequestBody Map<String, String> params) {
        try {
            Long circleId = Long.valueOf(params.get("circleId"));
            String title = params.get("title");
            String content = params.get("content");
            Topic topic = topicService.createTopic(userId, circleId, title, content);
            return Result.success("发布成功", topic);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/list/{circleId}")
    public Result<?> list(@PathVariable Long circleId,
                          @RequestParam(defaultValue = "1") Integer page,
                          @RequestParam(defaultValue = "20") Integer size) {
        try {
            List<Topic> topics = topicService.getTopicsByCircle(circleId, page, size);
            return Result.success(topics);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/detail/{id}")
    public Result<?> detail(@PathVariable Long id) {
        try {
            Topic topic = topicService.getTopicDetail(id);
            if (topic == null) return Result.error("话题不存在");
            return Result.success(topic);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/reply")
    public Result<?> reply(@RequestAttribute("userId") Long userId, @RequestBody Map<String, String> params) {
        try {
            Long topicId = Long.valueOf(params.get("topicId"));
            String content = params.get("content");
            TopicReply reply = topicService.addReply(userId, topicId, content);
            return Result.success("回复成功", reply);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/replies/{topicId}")
    public Result<?> replies(@PathVariable Long topicId) {
        try {
            List<TopicReply> replies = topicService.getReplies(topicId);
            return Result.success(replies);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@RequestAttribute("userId") Long userId, @PathVariable Long id) {
        try {
            Topic topic = topicService.getTopicById(id);
            if (topic == null) return Result.error("话题不存在");
            if (!topic.getUserId().equals(userId)) return Result.error(403, "无权删除此话题");
            topicService.deleteTopic(id);
            return Result.success("删除成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public Result<?> update(@RequestAttribute("userId") Long userId, @PathVariable Long id, @RequestBody Map<String, Object> params) {
        try {
            Topic topic = topicService.getTopicById(id);
            if (topic == null) return Result.error("话题不存在");
            if (!topic.getUserId().equals(userId)) return Result.error(403, "无权修改此话题");
            topicService.updateTopic(id, params);
            return Result.success("更新成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/all")
    public Result<?> listAll(@RequestParam(defaultValue = "1") Integer page,
                             @RequestParam(defaultValue = "100") Integer size) {
        try {
            List<Topic> topics = topicService.getAllTopics(page, size);
            return Result.success(topics);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}
