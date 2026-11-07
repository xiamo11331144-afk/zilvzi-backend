package com.zilvzi.mapper;

import com.zilvzi.pojo.VipOrder;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class VipMapperXmlTest {
    @Test void actualMapperXmlLoadsStatementsAndResolvesParametersWithoutDatabase() throws Exception {
        Configuration configuration = new Configuration();
        try (InputStream input = getClass().getResourceAsStream("/mapper/VipMapper.xml")) {
            assertNotNull(input, "VipMapper.xml must be available on the test classpath");
            XMLMapperBuilder builder = new XMLMapperBuilder(input, configuration,
                    "mapper/VipMapper.xml", configuration.getSqlFragments());
            builder.parse();
        }

        String namespace = "com.zilvzi.mapper.VipMapper.";
        for (String statement : new String[]{"selectVipUntil", "insertOrder", "selectOrder", "confirmOrder", "upsertVip"}) {
            assertTrue(configuration.hasStatement(namespace + statement), statement);
        }

        VipOrder order = new VipOrder();
        order.setOrderNo("xml-test");
        order.setUserId(7L);
        order.setPriceFen(990);
        order.setDurationDays(30);
        order.setPaymentChannel("mock");
        order.setStatus("PENDING");
        BoundSql insert = configuration.getMappedStatement(namespace + "insertOrder").getBoundSql(order);
        assertEquals(java.util.List.of("orderNo", "userId", "priceFen", "durationDays", "paymentChannel", "status"),
                insert.getParameterMappings().stream().map(mapping -> mapping.getProperty()).toList());

        BoundSql upsert = configuration.getMappedStatement(namespace + "upsertVip")
                .getBoundSql(Map.of("userId", 7L, "vipUntil", LocalDateTime.of(2026, 1, 1, 0, 0)));
        assertEquals(java.util.List.of("userId", "vipUntil"),
                upsert.getParameterMappings().stream().map(mapping -> mapping.getProperty()).toList());
        assertNotNull(configuration.getMappedStatement(namespace + "selectVipUntil").getBoundSql(Map.of("userId", 7L)));
        assertNotNull(configuration.getMappedStatement(namespace + "confirmOrder").getBoundSql(Map.of("orderNo", "xml-test")));
    }
}
