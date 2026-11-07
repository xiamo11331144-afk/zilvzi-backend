package com.zilvzi.controller;

import com.zilvzi.exception.BusinessException;
import com.zilvzi.pojo.Result;
import com.zilvzi.service.PartnerService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PartnerControllerVipTest {
    @Test void applicationUsesAuthenticatedIdAndPreservesVipErrorCode() {
        PartnerService service = mock(PartnerService.class);
        PartnerController controller = new PartnerController();
        ReflectionTestUtils.setField(controller, "partnerService", service);
        Result<?> accepted = controller.sendRequest(10L, Map.of(
                "partnerId", 20, "matchMode", "mutual", "userId", 999, "isVip", true));
        assertEquals(200, accepted.getCode());
        verify(service).sendRequest(10L, 20L, null, "mutual");

        doThrow(new BusinessException(40301, "VIP_REQUIRED"))
                .when(service).sendRequest(10L, 20L, null, "lead");
        Result<?> rejected = controller.sendRequest(10L, Map.of("partnerId", 20, "matchMode", "lead"));
        assertEquals(40301, rejected.getCode());
        assertEquals("VIP_REQUIRED", rejected.getMessage());
    }
}
