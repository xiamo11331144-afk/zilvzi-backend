package com.zilvzi.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CheckinMapperXmlTest {
    Configuration load() throws Exception {
        Configuration c = new Configuration();
        c.getTypeAliasRegistry().registerAliases("com.zilvzi.pojo");
        for (String file : new String[]{"CheckinMapper", "PlanTaskMapper", "GoalMapper"}) {
            String path="mapper/"+file+".xml";
            try (InputStream in=getClass().getResourceAsStream("/"+path)) {
                assertNotNull(in);
                new XMLMapperBuilder(in,c,path,c.getSqlFragments()).parse();
            }
        }
        return c;
    }
    String sql(Configuration c,String mapper,String id) {
        return c.getMappedStatement("com.zilvzi.mapper."+mapper+"."+id)
                .getBoundSql(Map.of("userId",1L,"year",2026,"month",11,"planId",2L,"id",3L))
                .getSql().replaceAll("\\s+"," ").trim();
    }
    @Test void reviewAndResubmissionAreConditionalAndBindingsLoad() throws Exception {
        Configuration c=load();
        String review=sql(c,"CheckinMapper","updateStatus");
        assertTrue(review.contains("WHERE id = ? AND status = 'pending'"));
        String resubmit=sql(c,"CheckinMapper","resubmitRejected");
        assertTrue(resubmit.contains("WHERE id = ? AND user_id = ? AND status = 'rejected'"));
        assertTrue(resubmit.contains("partner_id = NULL"));
        assertTrue(resubmit.contains("review_time = NULL"));
        assertTrue(sql(c,"GoalMapper","lockById").endsWith("FOR UPDATE"));
        assertTrue(sql(c,"CheckinMapper","selectCheckinDates").contains("status = 'approved'"));
        String pending=sql(c,"CheckinMapper","selectPendingForReview");
        assertTrue(pending.contains("g.user_id = c.user_id"));
        assertTrue(pending.contains("p.partner_id = c.user_id"));
        assertTrue(pending.contains("p.user_id = c.user_id"));
        assertTrue(pending.contains("p.status = 'accepted'"));
    }
    @Test void planTaskListsExposeReviewStatusForPastDays() throws Exception {
        Configuration c=load();
        for (String id : new String[]{"selectByPlanIdAndDay", "selectByPlanIdAndDate",
                "selectByPlanId", "selectByPlanIdAndPhase", "selectByPlanIdAndDayRange"}) {
            String s=sql(c,"PlanTaskMapper",id);
            assertTrue(s.contains("c.task_id = plan_task.id"),id);
            assertTrue(s.contains("AS checkin_status"),id);
            assertTrue(s.contains("ORDER BY c.checkin_time DESC, c.id DESC LIMIT 1"),id);
        }
        assertNotNull(com.zilvzi.pojo.PlanTask.class.getMethod("getCheckinStatus"));
    }
    @Test void pendingTasksCannotBeMarkedMissedOrDeletedByMaintenance() throws Exception {
        Configuration c=load();
        for(String id:new String[]{"selectIncompletePastDays","selectIncompletePastDaysExcludeSupplement",
                "countStreakBreakDays","selectStreakBreakTasks","deleteExpiredSupplementTasks",
                "countPendingDays","updateStreakBreakStatus"}) {
            String s=sql(c,"PlanTaskMapper",id);
            assertTrue(s.contains("NOT EXISTS"),id);
            assertTrue(s.contains("c.task_id = plan_task.id AND c.status = 'pending'"),id);
        }
        assertTrue(sql(c,"PlanTaskMapper","updateStreakBreakStatus").contains("completed != 1"));
    }
}
