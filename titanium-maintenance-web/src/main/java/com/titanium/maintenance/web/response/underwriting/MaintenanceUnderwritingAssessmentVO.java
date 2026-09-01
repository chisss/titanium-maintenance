package com.titanium.maintenance.web.response.underwriting;

import java.time.LocalDateTime;
import java.util.List;

import com.titanium.maintenance.common.enums.workflow.MaintenanceUnderwritingConclusion;

/** 触发或刷新保全核保后的权威结果。 */
public record MaintenanceUnderwritingAssessmentVO(
        String underwritingCaseId,
        MaintenanceUnderwritingConclusion conclusion,
        String ruleVersion,
        String modelVersion,
        List<String> additionalConditions,
        String summary,
        LocalDateTime completedAt) {

    public MaintenanceUnderwritingAssessmentVO {
        additionalConditions = List.copyOf(additionalConditions);
    }
}
