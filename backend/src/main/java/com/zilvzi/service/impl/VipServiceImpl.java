package com.zilvzi.service.impl;

import com.zilvzi.exception.BusinessException;
import com.zilvzi.mapper.UserMapper;
import com.zilvzi.mapper.VipMapper;
import com.zilvzi.pojo.VipOrder;
import com.zilvzi.service.VipService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class VipServiceImpl implements VipService {
    private final VipMapper vipMapper;
    private final UserMapper userMapper;
    private final Environment environment;
    @Value("${vip.plan.duration-days:30}") private int durationDays;
    @Value("${vip.plan.price-fen:990}") private int priceFen;
    @Value("${vip.payment.channel:disabled}") private String paymentChannel;
    @Value("${vip.mock-payment.enabled:false}") private boolean mockEnabled;

    public VipServiceImpl(VipMapper vipMapper, UserMapper userMapper, Environment environment) {
        this.vipMapper = vipMapper;
        this.userMapper = userMapper;
        this.environment = environment;
    }

    private boolean mockAllowed() {
        boolean devOrTest = false;
        for (String profile : environment.getActiveProfiles()) {
            String normalized = profile.toLowerCase();
            if (!normalized.equals("dev") && !normalized.equals("test")) return false;
            devOrTest = true;
        }
        return "mock".equalsIgnoreCase(paymentChannel == null ? "" : paymentChannel.trim())
                && mockEnabled && devOrTest;
    }

    static boolean activeAt(LocalDateTime until, LocalDateTime now) {
        return until != null && until.isAfter(now);
    }

    private LocalDateTime vipUntil(Long userId) {
        return vipMapper.selectVipUntil(userId);
    }

    @Override
    public boolean isVip(Long userId) {
        return activeAt(vipUntil(userId), LocalDateTime.now(ZoneOffset.UTC));
    }

    @Override
    public Map<String, Object> status(Long userId) {
        LocalDateTime until = vipUntil(userId);
        boolean active = activeAt(until, LocalDateTime.now(ZoneOffset.UTC));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("isVip", active);
        result.put("vipUntil", until == null ? null : OffsetDateTime.of(until, ZoneOffset.UTC).toString());
        return result;
    }

    @Override
    public Map<String, Object> plan() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("durationDays", durationDays);
        result.put("priceFen", priceFen);
        result.put("currency", "CNY");
        result.put("paymentMode", mockAllowed() ? "mock" : "disabled");
        result.put("mockEnabled", mockAllowed());
        return result;
    }

    private Map<String, Object> orderView(VipOrder order, String mode) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderNo", order.getOrderNo());
        result.put("status", order.getStatus());
        result.put("paymentMode", mode);
        result.put("priceFen", order.getPriceFen());
        result.put("durationDays", order.getDurationDays());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createOrder(Long userId) {
        if (durationDays < 1 || priceFen < 0) throw new IllegalStateException("VIP plan configuration is invalid");
        if (!mockAllowed()) throw new BusinessException(503, "支付渠道暂不可用");
        VipOrder order = new VipOrder();
        order.setOrderNo(UUID.randomUUID().toString().replace("-", ""));
        order.setUserId(userId);
        order.setPriceFen(priceFen);
        order.setDurationDays(durationDays);
        order.setPaymentChannel(paymentChannel.trim().toLowerCase());
        order.setStatus("PENDING");
        vipMapper.insertOrder(order);
        return orderView(order, mockAllowed() ? "mock" : "disabled");
    }

    private VipOrder ownedOrder(Long userId, String orderNo) {
        VipOrder order = vipMapper.selectOrder(orderNo);
        if (order == null || !userId.equals(order.getUserId())) throw new BusinessException(404, "订单不存在");
        return order;
    }

    @Override
    public Map<String, Object> getOrder(Long userId, String orderNo) {
        VipOrder order = ownedOrder(userId, orderNo);
        return orderView(order, mockAllowed() ? "mock" : "disabled");
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public Map<String, Object> mockConfirm(Long userId, String orderNo) {
        if (!mockAllowed()) throw new BusinessException(403, "模拟支付未启用");
        VipOrder order = ownedOrder(userId, orderNo);
        if (!"mock".equalsIgnoreCase(order.getPaymentChannel())) {
            throw new BusinessException(409, "订单支付渠道不支持模拟确认");
        }
        if ("TEST_CONFIRMED".equals(order.getStatus())) return orderView(order, "mock");
        if (!"PENDING".equals(order.getStatus())) throw new BusinessException(409, "订单状态不可确认");
        if (userMapper.selectByIdForUpdate(userId) == null) throw new BusinessException(404, "用户不存在");
        if (vipMapper.confirmOrder(orderNo) != 1) {
            order = ownedOrder(userId, orderNo);
            if (!"TEST_CONFIRMED".equals(order.getStatus())) throw new BusinessException(409, "订单状态已变化");
            return orderView(order, "mock");
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime current = vipUntil(userId);
        LocalDateTime base = current != null && current.isAfter(now) ? current : now;
        vipMapper.upsertVip(userId, base.plusDays(order.getDurationDays()));
        order = ownedOrder(userId, orderNo);
        return orderView(order, "mock");
    }
}
