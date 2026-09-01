package com.titanium.maintenance.application.model.casecreation;

import java.util.List;

import com.titanium.maintenance.common.enums.workflow.MaintenanceAutomaticReviewOutcome;

/** 自动审核结果；未通过门禁时保持原任务并返回转人工原因。 */
public record MaintenanceAutomaticReviewResult(
        MaintenanceAutomaticReviewOutcome outcome,
        String policyCode,
        String policyVersion,
        List<String> reasons) {

    public MaintenanceAutomaticReviewResult {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
}
