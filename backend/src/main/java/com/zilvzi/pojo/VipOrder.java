package com.zilvzi.pojo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class VipOrder {
    private Long id;
    private String orderNo;
    private Long userId;
    private Integer priceFen;
    private Integer durationDays;
    private String paymentChannel;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime confirmedTime;
}
