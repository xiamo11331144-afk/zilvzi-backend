package com.zilvzi.service.impl;

import com.zilvzi.mapper.PlanTemplateMapper;
import com.zilvzi.pojo.PlanTemplate;
import com.zilvzi.service.PlanCacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 方案缓存服务实现类
 */
@Service
public class PlanCacheServiceImpl implements PlanCacheService {

    @Autowired
    private PlanTemplateMapper planTemplateMapper;

    @Override
    public PlanTemplate matchTemplate(String goalType, String goalKeyword, Integer totalDays) {
        // 第一步：精确匹配
        PlanTemplate exact = planTemplateMapper.exactMatch(goalType, goalKeyword, totalDays);
        if (exact != null) {
            return exact;
        }

        // 第二步：模糊匹配（天数±7天，关键词相似）
        List<PlanTemplate> fuzzy = planTemplateMapper.fuzzyMatch(goalType, goalKeyword, totalDays, 7);
        if (fuzzy != null && !fuzzy.isEmpty()) {
            return fuzzy.get(0); // 返回评分最高的
        }

        return null; // 未匹配到
    }

    @Override
    public void saveTemplate(PlanTemplate template) {
        // 设置默认值
        if (template.getUsageCount() == null) template.setUsageCount(0);
        if (template.getSuccessCount() == null) template.setSuccessCount(0);
        if (template.getQualityScore() == null) template.setQualityScore(5.0f);
        if (template.getDifficulty() == null) template.setDifficulty("medium");

        planTemplateMapper.insert(template);
    }

    @Override
    public void incrementUsage(Long templateId) {
        planTemplateMapper.incrementUsage(templateId);
    }

    @Override
    public void incrementSuccess(Long templateId) {
        planTemplateMapper.incrementSuccess(templateId);
    }

    @Override
    public void updateScore(Long templateId, Float score) {
        planTemplateMapper.updateScore(templateId, score);
    }
}