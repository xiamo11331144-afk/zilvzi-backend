package com.zilvzi.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class NotificationMapperXmlTest {
    Configuration load() throws Exception {
        Configuration config = new Configuration();
        config.getTypeAliasRegistry().registerAliases("com.zilvzi.pojo");
        try (InputStream input = getClass().getResourceAsStream("/mapper/NotificationMapper.xml")) {
            assertNotNull(input);
            new XMLMapperBuilder(input, config, "mapper/NotificationMapper.xml", config.getSqlFragments()).parse();
        }
        return config;
    }

    @Test void everyNotificationMethodHasAnExecutableMapping() throws Exception {
        Configuration config = load();
        for (var method : NotificationMapper.class.getDeclaredMethods()) {
            assertTrue(config.hasStatement(NotificationMapper.class.getName() + "." + method.getName()),
                    "Missing notification statement: " + method.getName());
        }
    }

    @Test void ownershipLookupBindsIdAndMapsNotificationOwner() throws Exception {
        Configuration config = load();
        var statement = config.getMappedStatement(NotificationMapper.class.getName() + ".selectById");
        var bound = statement.getBoundSql(Map.of("id", 360L));
        assertTrue(bound.getSql().replaceAll("\\s+", " ").contains("WHERE n.id = ?"));
        assertEquals("id", bound.getParameterMappings().get(0).getProperty());
        assertTrue(statement.getResultMaps().get(0).getResultMappings().stream()
                .anyMatch(mapping -> "userId".equals(mapping.getProperty()) && "user_id".equals(mapping.getColumn())));
    }
}
