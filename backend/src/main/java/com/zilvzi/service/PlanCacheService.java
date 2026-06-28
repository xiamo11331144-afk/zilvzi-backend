package com.zilvzi.service;

import com.zilvzi.pojo.PlanTemplate;

/**
 * 方案缓存服务接口
 */
public interface PlanCacheService {

    /**
     * 匹配缓存方案
     * @param goalType 目标类型
     * @param goalKeyword 目标关键词
     * @param totalDays 总天数
     * @return 匹配到的模板，null表示未匹配到
     */
    PlanTemplate matchTemplate(String goalType, String goalKeyword, Integer totalDays);

    /**
     * 保存方案到缓存
     */
    void saveTemplate(PlanTemplate template);

    /**
     * 更新使用次数
     */
    void incrementUsage(Long templateId);

    /**
     * 更新成功次数
     */
    void incrementSuccess(Long templateId);

    /**
     * 更新质量评分
     */
    void updateScore(Long templateId, Float score);
}