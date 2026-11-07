package com.zilvzi.controller;

import com.zilvzi.pojo.Result;
import com.zilvzi.service.VipService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/vip")
public class VipController {
    private final VipService vipService;
    public VipController(VipService vipService) { this.vipService = vipService; }

    @GetMapping("/status")
    public Result<?> status(@RequestAttribute("userId") Long userId) { return Result.success(vipService.status(userId)); }

    @GetMapping("/plan")
    public Result<?> plan() { return Result.success(vipService.plan()); }

    @PostMapping("/orders")
    public Result<?> createOrder(@RequestAttribute("userId") Long userId) { return Result.success(vipService.createOrder(userId)); }

    @GetMapping("/orders/{orderNo}")
    public Result<?> getOrder(@RequestAttribute("userId") Long userId, @PathVariable String orderNo) {
        return Result.success(vipService.getOrder(userId, orderNo));
    }

    @PostMapping("/orders/{orderNo}/mock-confirm")
    public Result<?> mockConfirm(@RequestAttribute("userId") Long userId, @PathVariable String orderNo) {
        return Result.success(vipService.mockConfirm(userId, orderNo));
    }
}
