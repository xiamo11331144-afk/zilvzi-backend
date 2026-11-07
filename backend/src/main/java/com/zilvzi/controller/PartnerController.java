package com.zilvzi.controller;

import com.zilvzi.pojo.Partner;
import com.zilvzi.pojo.Result;
import com.zilvzi.exception.BusinessException;
import com.zilvzi.service.PartnerService;
import com.zilvzi.mapper.PartnerMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 搭子Controller
 */
@RestController
@RequestMapping("/api/partner")
public class PartnerController {

    @Autowired
    private PartnerService partnerService;

    @Autowired
    private PartnerMapper partnerMapper;

    /**
     * 获取我的搭子列表
     */
    @GetMapping("/list")
    public Result<?> getMyPartners(@RequestAttribute("userId") Long userId) {
        try {
            List<Map<String, Object>> partners = partnerService.getMyPartners(userId);
            return Result.success(partners);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取匹配推荐
     */
    @GetMapping("/match")
    public Result<?> getMatchRecommendations(@RequestAttribute("userId") Long userId,
                                             @RequestParam(required = false) String goalType,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false, defaultValue = "mutual") String matchMode) {
        try {
            List<Map<String, Object>> recommendations = partnerService.getMatchRecommendations(userId, goalType, keyword, matchMode);
            return Result.success(recommendations);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 发送搭子申请
     */
    @PostMapping("/apply")
    public Result<?> sendRequest(@RequestAttribute("userId") Long userId,
                                 @RequestBody Map<String, Object> params) {
        try {
            Long partnerId = Long.valueOf(params.get("partnerId").toString());
            Long goalId = params.get("goalId") != null ? Long.valueOf(params.get("goalId").toString()) : null;

            String matchMode = params.get("matchMode") == null ? "mutual" : params.get("matchMode").toString();
            partnerService.sendRequest(userId, partnerId, goalId, matchMode);
            return Result.success("申请已发送");
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取收到的申请
     */
    @GetMapping("/requests")
    public Result<?> getReceivedRequests(@RequestAttribute("userId") Long userId) {
        try {
            List<Map<String, Object>> requests = partnerService.getReceivedRequests(userId);
            return Result.success(requests);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 同意申请
     */
    @PutMapping("/accept/{requestId}")
    public Result<?> acceptRequest(@RequestAttribute("userId") Long userId, @PathVariable Long requestId) {
        try {
            // 校验操作者是否为请求接收方
            Partner partner = partnerMapper.selectById(requestId);
            if (partner == null || !partner.getPartnerId().equals(userId)) {
                return Result.error(403, "无权操作此申请");
            }
            partnerService.acceptRequest(userId, requestId);
            return Result.success("已同意");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 拒绝申请
     */
    @PutMapping("/reject/{requestId}")
    public Result<?> rejectRequest(@RequestAttribute("userId") Long userId, @PathVariable Long requestId) {
        try {
            // 校验操作者是否为请求接收方
            Partner partner = partnerMapper.selectById(requestId);
            if (partner == null || !partner.getPartnerId().equals(userId)) {
                return Result.error(403, "无权操作此申请");
            }
            partnerService.rejectRequest(userId, requestId);
            return Result.success("已拒绝");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 提醒搭子打卡
     */
    @PostMapping("/remind/{partnerId}")
    public Result<?> remindPartner(@RequestAttribute("userId") Long userId, @PathVariable Long partnerId) {
        try {
            partnerService.remindPartner(userId, partnerId);
            return Result.success("提醒已发送");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 解除搭子关系
     */
    @DeleteMapping("/remove/{partnerId}")
    public Result<?> removePartner(@RequestAttribute("userId") Long userId, @PathVariable Long partnerId) {
        try {
            partnerService.removePartner(userId, partnerId);
            return Result.success("已解除搭子关系");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 拉黑搭子
     */
    @PostMapping("/block/{partnerId}")
    public Result<?> blockPartner(@RequestAttribute("userId") Long userId, @PathVariable Long partnerId) {
        try {
            partnerService.blockPartner(userId, partnerId);
            return Result.success("已拉黑");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 解除拉黑
     */
    @PostMapping("/unblock/{partnerId}")
    public Result<?> unblockPartner(@RequestAttribute("userId") Long userId, @PathVariable Long partnerId) {
        try {
            partnerService.unblockPartner(userId, partnerId);
            return Result.success("已解除拉黑");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取拉黑列表
     */
    @GetMapping("/blocked")
    public Result<?> getBlockedPartners(@RequestAttribute("userId") Long userId) {
        try {
            List<Map<String, Object>> blocked = partnerService.getBlockedPartners(userId);
            return Result.success(blocked);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}