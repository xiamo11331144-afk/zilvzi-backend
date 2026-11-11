package com.zilvzi.service.impl;

import com.zilvzi.mapper.PlanTaskMapper;
import com.zilvzi.pojo.PlanTask;
import com.zilvzi.service.ExportService;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class ExportServiceImpl implements ExportService {
    @Autowired
    private PlanTaskMapper planTaskMapper;

    @Override
    public void exportCheckinExcel(Long userId, HttpServletResponse response) {
        List<PlanTask> tasks = planTaskMapper.selectCompletedByUserId(userId);
        StringBuilder csv = new StringBuilder("\uFEFF日期,任务名称,类型,状态,时长\r\n");
        for (PlanTask task : tasks) {
            csv.append(csvField(task.getTaskDate())).append(',')
                    .append(csvField(task.getTaskName())).append(',')
                    .append(csvField(task.getTaskType())).append(',')
                    .append(csvField(status(task))).append(',')
                    .append(csvField(task.getTaskDuration())).append("\r\n");
        }
        if (tasks.isEmpty()) csv.append("\"暂无打卡记录\",\"\",\"\",\"\",\"\"\r\n");
        writeResponse(response, "text/csv;charset=UTF-8", "checkin_records.csv",
                csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String csvField(Object value) {
        String text = value == null ? "" : value.toString();
        // Excel may execute formulas even when a CSV field is quoted. Check after leading whitespace/control bytes.
        String stripped = text.stripLeading();
        if (!stripped.isEmpty() && "=+-@".indexOf(stripped.charAt(0)) >= 0
                || text.startsWith("\t") || text.startsWith("\r") || text.startsWith("\n")) {
            text = "'" + text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private static String status(PlanTask task) {
        return Integer.valueOf(1).equals(task.getCompleted()) ? "已完成"
                : Integer.valueOf(-1).equals(task.getCompleted()) ? "已过期" : "未完成";
    }

    @Override
    public void exportCheckinPdf(Long userId, HttpServletResponse response) {
        List<PlanTask> tasks = planTaskMapper.selectCompletedByUserId(userId);
        // Build the whole document before touching the HTTP response so failure cannot masquerade as CSV.
        try (PDDocument doc = new PDDocument();
             InputStream input = ExportServiceImpl.class.getResourceAsStream("/DroidSansFallback.ttf");
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) throw new IOException("Missing bundled PDF font");
            PDType0Font font = PDType0Font.load(doc, input);
            List<String> lines = new ArrayList<>();
            for (PlanTask task : tasks) {
                String text = task.getTaskDate() + " [" + status(task) + "] "
                        + (task.getTaskName() == null ? "" : task.getTaskName());
                lines.addAll(wrap(font, printable(font, text), PDRectangle.A4.getWidth() - 100, 12));
            }
            if (lines.isEmpty()) lines.add("暂无打卡记录");
            int index = 0;
            while (index < lines.size()) {
                PDPage page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                    content.beginText();
                    content.setFont(font, 16);
                    content.newLineAtOffset(50, page.getMediaBox().getHeight() - 50);
                    content.showText("自律搭子 - 打卡记录（" + doc.getNumberOfPages() + "）");
                    content.setFont(font, 12);
                    content.newLineAtOffset(0, -32);
                    float y = page.getMediaBox().getHeight() - 82;
                    while (index < lines.size() && y >= 50) {
                        content.showText(lines.get(index++));
                        content.newLineAtOffset(0, -20);
                        y -= 20;
                    }
                    content.endText();
                }
            }
            doc.save(output);
            writeResponse(response, "application/pdf", "checkin_records.pdf", output.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("PDF导出失败，请稍后重试", e);
        }
    }

    /** Preserve unsupported glyphs as readable Unicode codes, rather than dropping a record. */
    private static String printable(PDType0Font font, String text) throws IOException {
        StringBuilder result = new StringBuilder();
        for (int cp : text.codePoints().toArray()) {
            if (cp == '\n' || cp == '\r') { result.append('\n'); continue; }
            if (Character.isISOControl(cp)) { result.append(' '); continue; }
            String glyph = new String(Character.toChars(cp));
            try {
                font.encode(glyph);
                result.append(glyph);
            } catch (IllegalArgumentException e) {
                result.append("[U+").append(Integer.toHexString(cp).toUpperCase(java.util.Locale.ROOT)).append(']');
            }
        }
        return result.toString();
    }

    private static List<String> wrap(PDType0Font font, String text, float width, float size) throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (int cp : text.codePoints().toArray()) {
            if (cp == '\n') { lines.add(line.toString()); line.setLength(0); continue; }
            String glyph = new String(Character.toChars(cp));
            if (!line.isEmpty() && font.getStringWidth(line.toString() + glyph) * size / 1000 > width) {
                lines.add(line.toString());
                line.setLength(0);
            }
            line.append(glyph);
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    private static void writeResponse(HttpServletResponse response, String type, String name, byte[] bytes) {
        try {
            response.reset();
            response.setContentType(type);
            response.setHeader("Content-Disposition", "attachment;filename=" + name);
            response.setContentLength(bytes.length);
            response.getOutputStream().write(bytes);
            response.getOutputStream().flush();
        } catch (IOException e) {
            throw new IllegalStateException("导出失败，请稍后重试", e);
        }
    }
}
