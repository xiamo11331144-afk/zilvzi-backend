package com.zilvzi.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UserMapperXmlTest {
    @Test void rankingQueriesCountOnlyApprovedCheckinsIncludingStreakDates() throws Exception {
        Configuration configuration = new Configuration();
        configuration.getTypeAliasRegistry().registerAliases("com.zilvzi.pojo");
        try (InputStream input = getClass().getResourceAsStream("/mapper/UserMapper.xml")) {
            assertNotNull(input);
            new XMLMapperBuilder(input, configuration, "mapper/UserMapper.xml", configuration.getSqlFragments()).parse();
        }
        Map<String, Object> params = new HashMap<>();
        params.put("limit", 10);
        params.put("today", java.time.LocalDate.of(2026, 11, 10));

        String totalsSql = configuration.getMappedStatement("com.zilvzi.mapper.UserMapper.selectRankingByCheckin")
                .getBoundSql(params).getSql().replaceAll("\\s+", " ").toLowerCase();
        assertTrue(totalsSql.contains("c.status = 'approved'"), totalsSql);

        String streakSql = configuration.getMappedStatement("com.zilvzi.mapper.UserMapper.selectRankingWithStreak")
                .getBoundSql(params).getSql().replaceAll("\\s+", " ").toLowerCase();
        assertTrue(streakSql.contains("c.status = 'approved'"), streakSql);
        assertTrue(streakSql.contains("where status = 'approved' and date <= ?"), streakSql);
    }

    @Test void mentorCandidateSqlLoadsAndBindsExplicitQualificationFiltersWithoutDatabase() throws Exception {
        Configuration configuration = new Configuration();
        configuration.getTypeAliasRegistry().registerAliases("com.zilvzi.pojo");
        try (InputStream input = getClass().getResourceAsStream("/mapper/UserMapper.xml")) {
            assertNotNull(input, "UserMapper.xml must be available on the test classpath");
            XMLMapperBuilder builder = new XMLMapperBuilder(input, configuration,
                    "mapper/UserMapper.xml", configuration.getSqlFragments());
            builder.parse();
        }

        String statementId = "com.zilvzi.mapper.UserMapper.selectMatchCandidates";
        assertTrue(configuration.hasStatement(statementId));
        Map<String, Object> params = new HashMap<>();
        params.put("excludeUserId", 10L);
        params.put("goalTypes", List.of("study"));
        params.put("province", "浙江");
        params.put("keyword", "小明");
        params.put("requireMentor", 1);
        params.put("requesterRate", 79.9);

        BoundSql boundSql = configuration.getMappedStatement(statementId).getBoundSql(params);
        String normalizedSql = boundSql.getSql().replaceAll("\\s+", " ").trim();
        assertTrue(normalizedSql.contains("a.approvedDays >= 24"));
        assertTrue(normalizedSql.contains("a.checkinRate >= 80"));
        assertTrue(normalizedSql.contains("a.streak >= 7"));
        assertTrue(normalizedSql.contains("a.checkinRate > ?"));
        assertTrue(normalizedSql.contains("u.mentor_enabled = 1"));
        assertTrue(normalizedSql.contains("g.type IN ( ? )"));
        assertTrue(normalizedSql.contains("u.nickname LIKE CONCAT('%', ?, '%')"));
        assertEquals(List.of("excludeUserId", "excludeUserId", "excludeUserId", "__frch_type_0",
                        "keyword", "keyword", "keyword", "requesterRate", "province"),
                boundSql.getParameterMappings().stream().map(mapping -> mapping.getProperty()).toList());

        params.put("requireMentor", 0);
        BoundSql mutualSql = configuration.getMappedStatement(statementId).getBoundSql(params);
        assertFalse(mutualSql.getSql().contains("a.checkinRate >= 80"));
        assertFalse(mutualSql.getSql().contains("u.mentor_enabled = 1"));
    }
}
