package com.zilvzi.service;

import java.util.Map;

public interface VipService {
    Map<String, Object> status(Long userId);
    Map<String, Object> plan();
    Map<String, Object> createOrder(Long userId);
    Map<String, Object> getOrder(Long userId, String orderNo);
    Map<String, Object> mockConfirm(Long userId, String orderNo);
    boolean isVip(Long userId);
}
