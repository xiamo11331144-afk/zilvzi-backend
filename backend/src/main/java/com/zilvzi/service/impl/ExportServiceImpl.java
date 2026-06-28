package com.zilvzi.service.impl;

import com.zilvzi.mapper.PlanTaskMapper;
import com.zilvzi.pojo.PlanTask;
import com.zilvzi.service.ExportService;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.io.*;
import java.util.List;

@Service
public class ExportServiceImpl implements ExportService {

    @Autowired
    private PlanTaskMapper planTaskMapper;

    @Override
    public void exportCheckinExcel(Long userId, HttpServletResponse response) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos, "UTF-8"));
            writer.println("日期,任务名称,类型,状态,时长");
            List<PlanTask> tasks = planTaskMapper.selectCompletedByUserId(userId);
            for (PlanTask task : tasks) {
                String status = task.getCompleted() == 1 ? "已完成" : (task.getCompleted() == -1 ? "已过期" : "未完成");
                writer.println(task.getTaskDate() + "," + task.getTaskName() + "," + task.getTaskType() + "," + status + "," + task.getTaskDuration());
            }
            if (tasks.isEmpty()) writer.println("暂无打卡记录");
            writer.flush(); writer.close();
            byte[] data = baos.toByteArray();
            response.reset();
            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment;filename=checkin_records.csv");
            response.setContentLength(data.length);
            response.getOutputStream().write(data);
            response.getOutputStream().flush();
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    public void exportCheckinPdf(Long userId, HttpServletResponse response) {
        try {
            PDDocument doc = new PDDocument();
            PDPage page = new PDPage();
            doc.addPage(page);
            PDPageContentStream cs = new PDPageContentStream(doc, page);
            
            // 尝试加载中文字体
            PDType0Font font = null;
            String[] fontPaths = {"C:/Windows/Fonts/simsun.ttc", "C:/Windows/Fonts/msyh.ttc", 
                                   "C:/Windows/Fonts/simhei.ttf", "C:/Windows/Fonts/simsun.ttf"};
            for (String path : fontPaths) {
                try { font = PDType0Font.load(doc, new File(path)); break; } catch (Exception ignored) {}
            }
            
            cs.beginText();
            cs.setLeading(24);
            cs.newLineAtOffset(50, 750);
            
            if (font != null) {
                cs.setFont(font, 16);
                cs.showText("自律搭子 - 打卡记录");
                cs.newLine(); cs.newLine();
                cs.setFont(font, 12);
            } else {
                try {
                    InputStream is = PDType0Font.class.getClassLoader()
                        .getResourceAsStream("org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf");
                    font = PDType0Font.load(doc, is);
                    cs.setFont(font, 12);
                } catch (Exception ignored) {}
                cs.showText("Zilvzi - Checkin Records");
                cs.newLine(); cs.newLine();
            }

            List<PlanTask> tasks = planTaskMapper.selectCompletedByUserId(userId);
            for (PlanTask task : tasks) {
                String status = task.getCompleted() == 1 ? "[v]" : "[x]";
                String text = task.getTaskDate() + " " + status + " " + task.getTaskName();
                cs.showText(text); cs.newLine();
            }
            if (tasks.isEmpty()) cs.showText("No records");

            cs.endText(); cs.close();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos); doc.close();

            response.reset();
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "attachment;filename=checkin_records.pdf");
            response.setContentLength(baos.size());
            response.getOutputStream().write(baos.toByteArray());
            response.getOutputStream().flush();
        } catch (Exception e) {
            exportCheckinExcel(userId, response);
        }
    }
}