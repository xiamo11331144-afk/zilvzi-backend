package com.zilvzi.service;

import com.zilvzi.pojo.Partner;
import java.util.List;
import java.util.Map;

/**
 * 搭子Service接口
 */
public interface PartnerService {

    /**
     * 获取我的搭子列表
     */
    List<Map<String, Object>> getMyPartners(Long userId);

    /**
     * 获取匹配推荐
     */
    List<Map<String, Object>> getMatchRecommendations(Long userId, String goalType, String keyword, String matchMode);

    /**
     * 发送搭子申请
     */
    void sendRequest(Long userId, Long partnerId, Long goalId, String matchMode);

    /**
     * 获取收到的申请
     */
    List<Map<String, Object>> getReceivedRequests(Long userId);

    /**
     * 同意申请
     */
    void acceptRequest(Long userId, Long requestId);

    /**
     * 拒绝申请
     */
    void rejectRequest(Long userId, Long requestId);

    /**
     * 提醒搭子打卡
     */
    void remindPartner(Long userId, Long partnerId);

    /**
     * 解除搭子关系
     */
    void removePartner(Long userId, Long partnerId);

    void blockPartner(Long userId, Long partnerId);

    void unblockPartner(Long userId, Long partnerId);

    List<Map<String, Object>> getBlockedPartners(Long userId);
}