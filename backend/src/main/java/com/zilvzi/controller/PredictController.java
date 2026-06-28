package com.zilvzi.controller;

import com.zilvzi.pojo.Result;
import com.zilvzi.service.AiService;
import com.zilvzi.service.CheckinService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/predict")
public class PredictController {

    @Autowired
    private AiService aiService;

    @Autowired
    private CheckinService checkinService;

    @GetMapping("/goal/{goalId}")
    public Result<?> predictGoal(@RequestAttribute("userId") Long userId, @PathVariable Long goalId) {
        try {
            Integer total = checkinService.getTotalCheckins(userId, goalId);
            total = total != null ? total : 0;
            Map<String, Object> data = new HashMap<>();
            data.put("totalCheckins", total);
            String prediction = aiService.getAdvice(data);
            return Result.success(prediction);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}