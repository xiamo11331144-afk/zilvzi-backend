package com.zilvzi.service;

import com.zilvzi.mapper.PlanMapper;
import com.zilvzi.mapper.PlanTaskMapper;
import com.zilvzi.pojo.Plan;
import com.zilvzi.pojo.PlanTask;
import com.zilvzi.service.impl.PlanServiceImpl;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlanGenerationTest {
    final PlanMapper plans = mock(PlanMapper.class);
    final PlanTaskMapper tasks = mock(PlanTaskMapper.class);
    final AiService ai = mock(AiService.class);
    final PlanServiceImpl service = new PlanServiceImpl();
    PlanGenerationTest() {
        ReflectionTestUtils.setField(service,"planMapper",plans);
        ReflectionTestUtils.setField(service,"planTaskMapper",tasks);
        ReflectionTestUtils.setField(service,"aiService",ai);
    }
    Plan plan() {
        Plan p = new Plan(); p.setId(10L); p.setName("学习计划");
        p.setOutline("{\"phases\":[{\"phase\":1,\"name\":\"开始\",\"startDay\":1,\"endDay\":1,\"theme\":\"基础\"}]}");
        when(plans.selectByIdForUpdate(10L)).thenReturn(p);
        when(plans.selectById(10L)).thenReturn(p); return p;
    }
    @Test void locksPlanBeforeCheckingPreviouslyGeneratedTasks() {
        plan(); List<PlanTask> existing = List.of(new PlanTask());
        when(tasks.selectByPlanIdAndPhase(10L,1,1)).thenReturn(existing);
        assertSame(existing,service.generatePhaseTasks(10L,1));
        var order = inOrder(plans,tasks);
        order.verify(plans).selectByIdForUpdate(10L);
        order.verify(tasks).selectIncompletePastDays(eq(10L),any(java.time.LocalDate.class));
        order.verify(tasks).selectByPlanIdAndPhase(10L,1,1);
        verifyNoInteractions(ai); verify(tasks,never()).batchInsert(anyList());
    }
    @Test void databaseInsertionFailurePropagatesWithoutRetryingAiOrFallback() {
        plan();
        when(ai.generatePhaseTasks(anyString(),anyString(),any(),anyInt(),anyInt(),anyString(),anyString()))
                .thenReturn("{\"dailyPlan\":[{\"tasks\":[{\"name\":\"阅读\",\"description\":\"读一章\",\"duration\":\"30分钟\"}]}]}");
        doThrow(new IllegalStateException("database unavailable")).when(tasks).batchInsert(anyList());
        IllegalStateException failure = assertThrows(IllegalStateException.class,()->service.generatePhaseTasks(10L,1));
        assertEquals("database unavailable",failure.getMessage());
        verify(ai,times(1)).generatePhaseTasks(anyString(),anyString(),any(),anyInt(),anyInt(),anyString(),anyString());
        verify(tasks,times(1)).batchInsert(anyList()); verify(plans,never()).update(any());
    }
    @Test void generationTransactionsUseReadCommittedAndRollbackAndMapperLocksRow() throws Exception {
        for(var method : List.of(PlanServiceImpl.class.getMethod("generatePhaseTasks",Long.class,Integer.class),
                PlanServiceImpl.class.getMethod("generatePlan",Long.class,Long.class,Map.class))) {
            Transactional tx = method.getAnnotation(Transactional.class);
            assertNotNull(tx); assertEquals(Isolation.READ_COMMITTED,tx.isolation());
            assertTrue(List.of(tx.rollbackFor()).contains(Exception.class));
        }
        Configuration config = new Configuration();
        config.getTypeAliasRegistry().registerAliases("com.zilvzi.pojo");
        try(InputStream input=getClass().getResourceAsStream("/mapper/PlanMapper.xml")) {
            new XMLMapperBuilder(input,config,"mapper/PlanMapper.xml",config.getSqlFragments()).parse();
        }
        String sql=config.getMappedStatement("com.zilvzi.mapper.PlanMapper.selectByIdForUpdate")
                .getBoundSql(Map.of("id",10L)).getSql().replaceAll("\\s+"," ").trim().toUpperCase();
        assertEquals("SELECT * FROM PLAN WHERE ID = ? FOR UPDATE",sql);
    }
}
