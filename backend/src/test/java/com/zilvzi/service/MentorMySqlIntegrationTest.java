package com.zilvzi.service;

import com.zilvzi.mapper.PartnerMapper;
import com.zilvzi.mapper.UserMapper;
import com.zilvzi.mapper.NotificationMapper;
import com.zilvzi.mapper.MessageMapper;
import com.zilvzi.pojo.Partner;
import com.zilvzi.service.impl.PartnerServiceImpl;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 显式设置 MENTOR_DB_TESTS=true 才运行；仅连接本机，独立测试账号逐个清理。 */
@SpringJUnitConfig(MentorMySqlIntegrationTest.Config.class)
@EnabledIfEnvironmentVariable(named = "MENTOR_DB_TESTS", matches = "true")
class MentorMySqlIntegrationTest {
    @Configuration
    @EnableTransactionManagement
    @MapperScan("com.zilvzi.mapper")
    @Import({PartnerServiceImpl.class, MentorMatchingService.class})
    static class Config {
        @Bean DataSource dataSource() {
            YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
            yaml.setResources(new ClassPathResource("application.yml"));
            Properties props = Objects.requireNonNull(yaml.getObject());
            String url = props.getProperty("spring.datasource.url");
            if (url == null || !url.matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1)(:[0-9]+)?/.*")) {
                throw new IllegalStateException("Integration tests require an explicitly enabled local datasource");
            }
            DriverManagerDataSource ds = new DriverManagerDataSource();
            ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
            ds.setUrl(url);
            ds.setUsername(props.getProperty("spring.datasource.username"));
            ds.setPassword(props.getProperty("spring.datasource.password"));
            return ds;
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception {
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(ds);
            factory.setTypeAliasesPackage("com.zilvzi.pojo");
            factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/*.xml"));
            org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
            configuration.setMapUnderscoreToCamelCase(true);
            factory.setConfiguration(configuration);
            return factory.getObject();
        }
        @Bean PlatformTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean NotificationService notificationService() { return mock(NotificationService.class); }
        @Bean AchievementService achievementService() { return mock(AchievementService.class); }
        @Bean VipService vipService() {
            VipService vip = mock(VipService.class);
            when(vip.isVip(anyLong())).thenReturn(true);
            return vip;
        }
        @Bean JdbcTemplate jdbcTemplate(DataSource ds) { return new JdbcTemplate(ds); }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired PartnerService partners;
    @Autowired MentorMatchingService mentors;
    @Autowired UserMapper users;
    @Autowired PartnerMapper relationships;
    @Autowired NotificationMapper notifications;
    @Autowired MessageMapper messages;
    @Autowired PlatformTransactionManager transactionManager;
    private final Map<Long, String> testUsers = new LinkedHashMap<>();
    private final String marker = "__mentor_it_" + UUID.randomUUID().toString().substring(0, 8);
    private LocalDate today;

    @BeforeEach void databaseDate() { today = jdbc.queryForObject("SELECT CURDATE()", LocalDate.class); }

    private long createUser(String suffix, boolean enabled) {
        String name = marker + suffix;
        long id = insert("INSERT INTO user(username,nickname,mentor_enabled,level) VALUES(?,?,?,1)", name, name, enabled ? 1 : 0);
        testUsers.put(id, name);
        return id;
    }

    private long insert(String sql, Object... values) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            return statement;
        }, keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }

    private long goal(long userId) {
        return insert("INSERT INTO goal(user_id,name,type,status) VALUES(?,?,?,'active')", userId, marker, "学习");
    }
    private void checkin(long userId, long goalId, int offset, String status) {
        jdbc.update("INSERT INTO checkin(user_id,goal_id,date,status) VALUES(?,?,?,?)", userId, goalId, today.plusDays(offset), status);
    }
    private long qualifiedMentor() {
        long id = createUser("_mentor", true);
        long goalId = goal(id);
        for (int i = 0; i < 24; i++) checkin(id, goalId, -i, "approved");
        return id;
    }
    private Partner request(long applicant, long mentor) { return relationships.selectByUserAndPartner(applicant, mentor); }

    @AfterEach void cleanupOnlyOwnedAccounts() {
        for (var entry : testUsers.entrySet()) {
            jdbc.update("DELETE FROM user WHERE id=? AND username=?", entry.getKey(), entry.getValue());
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE username LIKE ?", Integer.class, marker + "%"));
    }

    private void notification(long userId, String type) {
        jdbc.update("INSERT INTO notification(user_id,from_user_id,type,title,content,is_read) VALUES(?,NULL,?,?,?,0)",
                userId, type, type, marker);
    }

    @Test void notificationCategoriesFilterAndMutateOnlyCurrentUserAndCategory() {
        long user = createUser("_notice_owner", false);
        long other = createUser("_notice_other", false);
        notification(user, "comment");
        notification(user, "like");
        notification(user, "topic_reply");
        notification(user, "private_message");
        notification(user, "partner_apply");
        notification(user, "checkin_approved");
        notification(user, "partner_reject");
        notification(other, "comment");

        assertEquals(3, notifications.countUnreadByCategory(user, "circle"));
        assertEquals(2, notifications.countUnreadByCategory(user, "notice"));
        assertEquals(7, notifications.countUnreadByCategory(user, "all"));
        Map<String, Object> badgeCounts = notifications.selectUnreadBadgeCounts(user);
        assertEquals(2, ((Number) badgeCounts.get("notificationUnread")).intValue());
        assertEquals(3, ((Number) badgeCounts.get("circleUnread")).intValue());
        assertEquals(3, notifications.selectByUserIdAndCategory(user, "circle", 0, 10).size());
        assertEquals(2, notifications.selectByUserIdAndCategory(user, "notice", 0, 10).size());

        notifications.markAllAsReadByCategory(user, "circle");
        assertEquals(0, notifications.countUnreadByCategory(user, "circle"));
        assertEquals(2, notifications.countUnreadByCategory(user, "notice"));
        assertEquals(1, notifications.countUnreadByCategory(other, "circle"));
        notifications.deleteByCategory(user, "notice");
        assertEquals(0, notifications.selectByUserIdAndCategory(user, "notice", 0, 10).size());
        assertEquals(1, notifications.selectByUserIdAndCategory(other, "circle", 0, 10).size());
    }

    @Test void messageUnreadAndPendingRequestCountsUseTheirOwnTablesAndRecipientDirection() {
        long recipient = createUser("_badge_recipient", false);
        long sender = createUser("_badge_sender", false);
        long another = createUser("_badge_another", false);
        jdbc.update("INSERT INTO message(from_user_id,to_user_id,content,is_read) VALUES(?,?,?,0)", sender, recipient, marker);
        jdbc.update("INSERT INTO message(from_user_id,to_user_id,content,is_read) VALUES(?,?,?,0)", another, recipient, marker);
        jdbc.update("INSERT INTO message(from_user_id,to_user_id,content,is_read) VALUES(?,?,?,1)", sender, recipient, marker);
        assertEquals(2, messages.countUnread(recipient));
        assertEquals(0, messages.countUnread(sender));

        jdbc.update("INSERT INTO partner(user_id,partner_id,match_mode,status) VALUES(?,?,'mutual','pending')", sender, recipient);
        jdbc.update("INSERT INTO partner(user_id,partner_id,match_mode,status) VALUES(?,?,'lead','pending')", recipient, another);
        assertEquals(1, relationships.countPendingReceivedRequests(recipient));
        assertEquals(0, relationships.countPendingReceivedRequests(sender));
    }

    @Test void messagePageReadsOnlyReturnedInboundMessagesFromThatConversation() {
        long user = createUser("_message_user", false);
        long sender = createUser("_message_sender", false);
        long other = createUser("_message_other", false);
        long oldest = insert("INSERT INTO message(from_user_id,to_user_id,content,is_read,create_time) VALUES(?,?,?,0,DATE_SUB(NOW(), INTERVAL 3 MINUTE))", sender, user, marker);
        long middle = insert("INSERT INTO message(from_user_id,to_user_id,content,is_read,create_time) VALUES(?,?,?,0,DATE_SUB(NOW(), INTERVAL 2 MINUTE))", sender, user, marker);
        long newest = insert("INSERT INTO message(from_user_id,to_user_id,content,is_read,create_time) VALUES(?,?,?,0,DATE_SUB(NOW(), INTERVAL 1 MINUTE))", user, sender, marker);
        long otherConversation = insert("INSERT INTO message(from_user_id,to_user_id,content,is_read,create_time) VALUES(?,?,?,0,NOW())", other, user, marker);

        List<com.zilvzi.pojo.Message> latestPage = messages.selectConversation(user, sender, 0, 2);
        assertEquals(List.of(newest, middle), latestPage.stream().map(com.zilvzi.pojo.Message::getId).toList());
        messages.markMessagesAsRead(user, sender, List.of(middle));

        assertEquals(2, messages.countUnread(user));
        assertEquals(1, jdbc.queryForObject("SELECT is_read FROM message WHERE id=?", Integer.class, middle));
        assertEquals(0, jdbc.queryForObject("SELECT is_read FROM message WHERE id=?", Integer.class, oldest));
        assertEquals(0, jdbc.queryForObject("SELECT is_read FROM message WHERE id=?", Integer.class, otherConversation));
    }

    @Test void realSqlCountsDistinctApprovedDaysAndHandlesYesterdayAndFuture() {
        long mentor = qualifiedMentor();
        long secondGoal = goal(mentor);
        checkin(mentor, secondGoal, 0, "approved");
        checkin(mentor, secondGoal, 1, "approved");
        checkin(mentor, secondGoal, -24, "pending");
        checkin(mentor, secondGoal, -25, "rejected");
        var stats = mentors.getStatus(mentor);
        assertEquals(24, stats.get("approvedDays"));
        assertEquals(24, stats.get("streak"));
        assertEquals(80D, stats.get("checkinRate"));
        assertEquals(true, stats.get("eligible"));
        jdbc.update("UPDATE checkin SET status='rejected' WHERE user_id=? AND date=?", mentor, today);
        assertEquals(23D, MentorMatchingService.number(users.selectMentorStats(mentor), "streak"));
        jdbc.update("UPDATE checkin SET status='rejected' WHERE user_id=? AND date=?", mentor, today.minusDays(1));
        assertEquals(0D, MentorMatchingService.number(users.selectMentorStats(mentor), "streak"));
        long boundaryUser = createUser("_boundary", false);
        long boundaryGoal = goal(boundaryUser);
        checkin(boundaryUser, boundaryGoal, -29, "approved");
        checkin(boundaryUser, boundaryGoal, -30, "approved");
        assertEquals(1D, MentorMatchingService.number(users.selectMentorStats(boundaryUser), "approvedDays"));
        assertEquals(3.3D, MentorMatchingService.number(users.selectMentorStats(boundaryUser), "checkinRate"));
    }

    @Test void fiveConcurrentApplicantsCanReserveOnlyThreeSlotsAndAcceptAtCapacity() throws Exception {
        long mentor = qualifiedMentor();
        List<Long> applicants = new ArrayList<>();
        for (int i = 0; i < 5; i++) applicants.add(createUser("_a" + i, false));
        assertTrue(partners.getMatchRecommendations(applicants.get(0), "study", marker, "lead").stream()
                .anyMatch(row -> ((Number) row.get("id")).longValue() == mentor));
        ExecutorService pool = Executors.newFixedThreadPool(5);
        CountDownLatch ready = new CountDownLatch(5);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> attempts = new ArrayList<>();
        try {
            for (long applicant : applicants) attempts.add(pool.submit(() -> {
                ready.countDown();
                if (!start.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Start barrier timed out");
                try { partners.sendRequest(applicant, mentor, null, "lead"); return true; }
                catch (IllegalArgumentException ex) {
                    assertTrue(ex.getMessage().contains("名额已满"), ex.getMessage());
                    return false;
                }
            }));
            assertTrue(ready.await(15, TimeUnit.SECONDS));
            start.countDown();
            int success = 0;
            for (var attempt : attempts) if (attempt.get(30, TimeUnit.SECONDS)) success++;
            assertEquals(3, success);
        } finally { start.countDown(); pool.shutdownNow(); assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS)); }
        assertEquals(3, mentors.getStatus(mentor).get("occupiedSlots"));
        List<Long> successful = applicants.stream().filter(id -> request(id, mentor) != null).toList();
        long failed = applicants.stream().filter(id -> request(id, mentor) == null).findFirst().orElseThrow();
        assertTrue(partners.getMatchRecommendations(failed, null, marker, "lead").isEmpty());
        Partner first = request(successful.get(0), mentor);
        partners.acceptRequest(mentor, first.getId());
        assertEquals("lead", relationships.selectById(first.getId()).getMatchMode());
        assertEquals(1, mentors.getStatus(mentor).get("acceptedCount"));
        assertEquals(3, mentors.getStatus(mentor).get("occupiedSlots"));
        assertThrows(IllegalArgumentException.class, () -> partners.acceptRequest(mentor, first.getId()));
        partners.rejectRequest(mentor, request(successful.get(1), mentor).getId());
        assertEquals(2, mentors.getStatus(mentor).get("occupiedSlots"));
        assertEquals(1, partners.getMatchRecommendations(failed, "学习", marker, "lead").size());
        partners.sendRequest(failed, mentor, null, "lead");
        assertEquals(3, mentors.getStatus(mentor).get("occupiedSlots"));
    }

    @Test void blockingReleasesLeadSlotAndCannotBeBypassedByRemovingRelationship() {
        long mentor = qualifiedMentor();
        long applicant = createUser("_blocked", false);
        partners.sendRequest(applicant, mentor, null, "lead");
        partners.acceptRequest(mentor, request(applicant, mentor).getId());
        jdbc.update("UPDATE user SET mentor_enabled=0 WHERE id=?", mentor);
        assertEquals(1, mentors.getStatus(mentor).get("acceptedCount"));
        assertEquals("lead", partners.getMyPartners(applicant).get(0).get("matchMode"));
        jdbc.update("UPDATE user SET mentor_enabled=1 WHERE id=?", mentor);
        partners.blockPartner(mentor, applicant);
        assertEquals(0, mentors.getStatus(mentor).get("occupiedSlots"));
        assertThrows(IllegalArgumentException.class, () -> partners.removePartner(applicant, mentor));
        assertThrows(IllegalArgumentException.class, () -> partners.sendRequest(applicant, mentor, null, "lead"));
        partners.unblockPartner(mentor, applicant);
        assertEquals("rejected", request(applicant, mentor).getStatus());
        assertEquals(0, mentors.getStatus(mentor).get("occupiedSlots"));
        partners.sendRequest(applicant, mentor, null, "lead");
        assertEquals(1, mentors.getStatus(mentor).get("occupiedSlots"));
    }

    @Test void mapperRereadInSameTransactionMustObserveLatestRequestState() {
        long mentor = qualifiedMentor();
        long applicant = createUser("_reread", false);
        partners.sendRequest(applicant, mentor, null, "lead");
        long requestId = request(applicant, mentor).getId();
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            Partner before = relationships.selectById(requestId);
            assertEquals("pending", before.getStatus());
            // JDBC更新不触发MyBatis的缓存清理，模拟锁等待期间另一事务已完成状态变更。
            jdbc.update("UPDATE partner SET status='rejected' WHERE id=?", requestId);
            Partner after = relationships.selectById(requestId);
            assertEquals("rejected", after.getStatus());
            assertNotSame(before, after);
        });
    }

    @Test void eligibilityAndStrongerActivityAreRecheckedOnApplyAndAcceptAndMutualIsCompatible() {
        long mentor = qualifiedMentor();
        long applicant = createUser("_applicant", false);
        partners.sendRequest(applicant, mentor, null, "lead");
        Partner pending = request(applicant, mentor);
        jdbc.update("UPDATE user SET mentor_enabled=0 WHERE id=?", mentor);
        assertThrows(IllegalArgumentException.class, () -> partners.acceptRequest(mentor, pending.getId()));
        assertEquals("pending", relationships.selectById(pending.getId()).getStatus());
        jdbc.update("UPDATE user SET mentor_enabled=1 WHERE id=?", mentor);
        long applicantGoal = goal(applicant);
        for (int i = 0; i < 24; i++) checkin(applicant, applicantGoal, -i, "approved");
        assertThrows(IllegalArgumentException.class, () -> partners.acceptRequest(mentor, pending.getId()));
        partners.rejectRequest(mentor, pending.getId());
        assertThrows(IllegalArgumentException.class, () -> partners.sendRequest(applicant, mentor, null, "lead"));
        partners.sendRequest(applicant, mentor, null, null);
        assertEquals("mutual", request(applicant, mentor).getMatchMode());
        assertEquals(0, mentors.getStatus(mentor).get("occupiedSlots"));
        partners.acceptRequest(mentor, pending.getId());
        assertEquals("mutual", partners.getMyPartners(applicant).get(0).get("matchMode"));
        long weakApplicant = createUser("_weak", false);
        jdbc.update("UPDATE checkin SET status='rejected' WHERE user_id=? AND date=?", mentor, today);
        assertThrows(IllegalArgumentException.class, () -> partners.sendRequest(weakApplicant, mentor, null, "lead"));
        assertTrue(partners.getMatchRecommendations(weakApplicant, null, marker + "_mentor", "lead").isEmpty());
    }
}
