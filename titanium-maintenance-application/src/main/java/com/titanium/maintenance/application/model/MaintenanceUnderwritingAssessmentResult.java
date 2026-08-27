package com.titanium.maintenance.application.model;

import java.time.LocalDateTime;
import java.util.List;

import com.titanium.maintenance.common.enums.workflow.MaintenanceUnderwritingConclusion;

/** 已写入案件任务的核保结论响应。 */
public record MaintenanceUnderwritingAssessmentResult(
        String underwritingCaseId,
        MaintenanceUnderwritingConclusion conclusion,
        String ruleVersion,
        String modelVersion,
        List<String> additionalConditions,
        String summary,
        LocalDateTime completedAt) {

    public MaintenanceUnderwritingAssessmentResult {
        additionalConditions = List.copyOf(additionalConditions);
    }
}
