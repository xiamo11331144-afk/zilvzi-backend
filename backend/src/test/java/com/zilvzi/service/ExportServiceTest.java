package com.zilvzi.service;

import com.zilvzi.mapper.PlanTaskMapper;
import com.zilvzi.pojo.PlanTask;
import com.zilvzi.service.impl.ExportServiceImpl;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExportServiceTest {
    final PlanTaskMapper tasks = mock(PlanTaskMapper.class);
    final ExportServiceImpl service = new ExportServiceImpl();
    ExportServiceTest() { ReflectionTestUtils.setField(service, "planTaskMapper", tasks); }
    PlanTask task(String name) {
        PlanTask task = new PlanTask(); task.setTaskDate(LocalDate.of(2026,11,10));
        task.setTaskName(name); task.setTaskType("学习"); task.setCompleted(1); task.setTaskDuration("30分钟");
        return task;
    }
    @Test void csvRoundTripsCommasQuotesAndNewlinesAndDisarmsFormulas() throws Exception {
        List<String> names = List.of("阅读,写作\n复习\"重点\"", "=1+1", "+SUM(A1)", "-1+2", "@SUM(A1)", "  =1+1", "\t危险");
        when(tasks.selectCompletedByUserId(1L)).thenReturn(names.stream().map(this::task).toList());
        MockHttpServletResponse response = new MockHttpServletResponse(); service.exportCheckinExcel(1L,response);
        String csv = response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("\uFEFF"));
        List<List<String>> rows = parseCsv(csv.substring(1));
        assertEquals(8,rows.size());
        rows.forEach(row -> assertEquals(5,row.size()));
        assertEquals(names.get(0),rows.get(1).get(1));
        for (int i=1;i<names.size();i++) assertEquals("'"+names.get(i),rows.get(i+1).get(1));
        assertTrue(response.getContentType().startsWith("text/csv"));
    }
    @Test void pdfPaginatesWrapsChineseAndPreservesLastRecordAndUnsupportedUnicode() throws Exception {
        List<PlanTask> list = new ArrayList<>();
        for(int i=0;i<80;i++) list.add(task("记录"+i+" 学习内容".repeat(15)));
        list.add(task("FINAL_RECORD_80 中文结束 😀"));
        when(tasks.selectCompletedByUserId(1L)).thenReturn(list);
        MockHttpServletResponse response = new MockHttpServletResponse(); service.exportCheckinPdf(1L,response);
        assertEquals("application/pdf",response.getContentType());
        assertEquals("%PDF",new String(response.getContentAsByteArray(),0,4,java.nio.charset.StandardCharsets.US_ASCII));
        try(PDDocument doc = Loader.loadPDF(response.getContentAsByteArray())) {
            assertTrue(doc.getNumberOfPages()>=4);
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override protected void processTextPosition(TextPosition text) {
                    assertTrue(text.getXDirAdj()>=49 && text.getXDirAdj()+text.getWidthDirAdj()<550,"Text exceeds horizontal margin");
                    assertTrue(text.getYDirAdj()<800,"Text below page");
                    super.processTextPosition(text);
                }
            };
            String text = stripper.getText(doc);
            for(int i=0;i<80;i++) assertTrue(text.contains("记录"+i+" "),"Missing record "+i);
            assertTrue(text.contains("FINAL_RECORD_80")); assertTrue(text.contains("中文结束"));
            assertTrue(text.contains("[U+1F600]"));
        }
    }
    @Test void emptyExportsStillHaveValidFormats() throws Exception {
        when(tasks.selectCompletedByUserId(1L)).thenReturn(List.of());
        MockHttpServletResponse csv = new MockHttpServletResponse(); service.exportCheckinExcel(1L,csv);
        assertEquals(5,parseCsv(csv.getContentAsString(java.nio.charset.StandardCharsets.UTF_8).substring(1)).get(1).size());
        MockHttpServletResponse pdf = new MockHttpServletResponse(); service.exportCheckinPdf(1L,pdf);
        try(PDDocument doc = Loader.loadPDF(pdf.getContentAsByteArray())) {
            assertEquals(1,doc.getNumberOfPages()); assertTrue(new PDFTextStripper().getText(doc).contains("暂无打卡记录"));
        }
    }
    private static List<List<String>> parseCsv(String input) {
        List<List<String>> rows = new ArrayList<>(); List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder(); boolean quoted = false;
        for(int i=0;i<input.length();i++) {
            char c=input.charAt(i);
            if(c=='"') {
                if(quoted && i+1<input.length() && input.charAt(i+1)=='"') {field.append('"');i++;}
                else quoted=!quoted;
            } else if(c==',' && !quoted) {row.add(field.toString());field.setLength(0);}
            else if(c=='\r' && !quoted && i+1<input.length() && input.charAt(i+1)=='\n') {
                row.add(field.toString());rows.add(row);row=new ArrayList<>();field.setLength(0);i++;
            } else field.append(c);
        }
        assertFalse(quoted); return rows;
    }
}
