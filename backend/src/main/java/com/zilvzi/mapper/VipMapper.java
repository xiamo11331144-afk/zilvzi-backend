package com.zilvzi.mapper;

import com.zilvzi.pojo.VipOrder;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;

public interface VipMapper {
    LocalDateTime selectVipUntil(@Param("userId") Long userId);
    int insertOrder(VipOrder order);
    VipOrder selectOrder(@Param("orderNo") String orderNo);
    int confirmOrder(@Param("orderNo") String orderNo);
    int upsertVip(@Param("userId") Long userId, @Param("vipUntil") LocalDateTime vipUntil);
}
