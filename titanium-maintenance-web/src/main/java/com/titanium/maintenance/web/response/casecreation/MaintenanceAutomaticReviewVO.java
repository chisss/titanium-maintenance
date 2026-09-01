package com.titanium.maintenance.web.response.casecreation;

import java.util.List;

import com.titanium.maintenance.common.enums.workflow.MaintenanceAutomaticReviewOutcome;

/** 自动审核结果；转人工时返回稳定原因码。 */
public record MaintenanceAutomaticReviewVO(
        MaintenanceAutomaticReviewOutcome outcome,
        String policyCode,
        String policyVersion,
        List<String> reasons) {

    public MaintenanceAutomaticReviewVO {
        reasons = List.copyOf(reasons);
    }
}
