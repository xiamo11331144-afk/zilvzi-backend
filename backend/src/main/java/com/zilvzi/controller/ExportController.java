package com.zilvzi.controller;

import com.zilvzi.pojo.Result;
import com.zilvzi.service.ExportService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/export")
public class ExportController {

    @Autowired
    private ExportService exportService;

    @GetMapping("/checkin/excel")
    public void exportCheckinExcel(@RequestAttribute("userId") Long userId,
                                    HttpServletResponse response) {
        exportService.exportCheckinExcel(userId, response);
    }

    @GetMapping("/checkin/pdf")
    public void exportCheckinPdf(@RequestAttribute("userId") Long userId,
                                  HttpServletResponse response) {
        exportService.exportCheckinPdf(userId, response);
    }
}