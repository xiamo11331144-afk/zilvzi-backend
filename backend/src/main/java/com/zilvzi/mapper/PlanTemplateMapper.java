package com.zilvzi.mapper;

import com.zilvzi.pojo.PlanTemplate;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 方案模板Mapper接口
 */
public interface PlanTemplateMapper {

    /**
     * 插入模板
     */
    int insert(PlanTemplate template);

    /**
     * 精确匹配：目标类型 + 关键词 + 天数
     */
    PlanTemplate exactMatch(@Param("goalType") String goalType, @Param("goalKeyword") String goalKeyword, @Param("totalDays") Integer totalDays);

    /**
     * 模糊匹配：目标类型 + 天数范围 + 关键词相似
     */
    List<PlanTemplate> fuzzyMatch(@Param("goalType") String goalType, @Param("goalKeyword") String goalKeyword, @Param("totalDays") Integer totalDays, @Param("daysRange") Integer daysRange);

    /**
     * 更新使用次数
     */
    int incrementUsage(@Param("id") Long id);

    /**
     * 更新成功次数
     */
    int incrementSuccess(@Param("id") Long id);

    /**
     * 更新质量评分
     */
    int updateScore(@Param("id") Long id, @Param("score") Float score);

    /**
     * 获取热门模板
     */
    List<PlanTemplate> getTopTemplates(@Param("limit") Integer limit);
}