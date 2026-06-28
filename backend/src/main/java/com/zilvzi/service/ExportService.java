package com.zilvzi.service;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 数据导出Service接口
 */
public interface ExportService {

    /**
     * 导出打卡记录为Excel
     */
    void exportCheckinExcel(Long userId, HttpServletResponse response);

    /**
     * 导出打卡记录为PDF
     */
    void exportCheckinPdf(Long userId, HttpServletResponse response);
}