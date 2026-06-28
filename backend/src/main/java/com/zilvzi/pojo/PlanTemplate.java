package com.zilvzi.pojo;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

/**
 * 方案模板实体类（动态缓存）
 */
public class PlanTemplate {
    private Long id;
    private String goalType;
    private String goalKeyword;
    private Integer totalDays;
    private String difficulty;
    private String planOutline;
    private String dailyTasksTemplate;
    private String aiModel;
    private Integer usageCount;
    private Integer successCount;
    private Float qualityScore;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    // getter/setter
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getGoalType() { return goalType; }
    public void setGoalType(String goalType) { this.goalType = goalType; }
    public String getGoalKeyword() { return goalKeyword; }
    public void setGoalKeyword(String goalKeyword) { this.goalKeyword = goalKeyword; }
    public Integer getTotalDays() { return totalDays; }
    public void setTotalDays(Integer totalDays) { this.totalDays = totalDays; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public String getPlanOutline() { return planOutline; }
    public void setPlanOutline(String planOutline) { this.planOutline = planOutline; }
    public String getDailyTasksTemplate() { return dailyTasksTemplate; }
    public void setDailyTasksTemplate(String dailyTasksTemplate) { this.dailyTasksTemplate = dailyTasksTemplate; }
    public String getAiModel() { return aiModel; }
    public void setAiModel(String aiModel) { this.aiModel = aiModel; }
    public Integer getUsageCount() { return usageCount; }
    public void setUsageCount(Integer usageCount) { this.usageCount = usageCount; }
    public Integer getSuccessCount() { return successCount; }
    public void setSuccessCount(Integer successCount) { this.successCount = successCount; }
    public Float getQualityScore() { return qualityScore; }
    public void setQualityScore(Float qualityScore) { this.qualityScore = qualityScore; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}