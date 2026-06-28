package com.zilvzi.controller;

import com.zilvzi.pojo.Result;
import com.zilvzi.utils.SensitiveWordFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    @Autowired
    private SensitiveWordFilter filter;

    @PostMapping("/check")
    public Result<?> checkContent(@RequestBody java.util.Map<String, String> params) {
        String content = params.get("content");
        if (filter.containsSensitive(content)) {
            return Result.error("内容包含敏感词");
        }
        return Result.success("通过审核");
    }
}