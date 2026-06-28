package com.zilvzi.controller;

import com.zilvzi.pojo.Result;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

/**
 * 欢迎Controller
 */
@Controller
public class IndexController {

    @GetMapping("/")
    @ResponseBody
    public Result<?> index() {
        Map<String, Object> data = new HashMap<>();
        data.put("name", "自律搭子监督与习惯养成平台");
        data.put("version", "1.0.0");
        data.put("status", "running");
        return Result.success(data);
    }

    @GetMapping("/admin")
    public String admin() {
        return "forward:/admin/index.html";
    }

    @GetMapping("/api/test")
    @ResponseBody
    public Result<?> test() {
        return Result.success("接口测试成功");
    }
}