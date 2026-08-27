package com.titanium.maintenance.application.command;

import java.util.List;

import com.titanium.maintenance.common.enums.config.MaintenanceChannel;

/** API 自动审核提交的外部证据摘要，配置和产品证据由案件冻结事实派生。 */
public record MaintenanceAutomaticReviewInput(
        String maintenanceId,
        String taskId,
        String operationId,
        String policyVersion,
        Boolean identityVerified,
        String identityEvidenceHash,
        List<String> satisfiedMaterialCodes,
        String materialEvidenceHash,
        Boolean amountWithinLimit,
        String amountEvidenceHash,
        Boolean riskAccepted,
        String riskEvidenceHash,
        String operatorId,
        String tenantId,
        MaintenanceChannel source) {

    public MaintenanceAutomaticReviewInput {
        satisfiedMaterialCodes = satisfiedMaterialCodes == null
                ? List.of()
                : satisfiedMaterialCodes.stream()
                        .filter(code -> code != null && !code.isBlank())
                        .map(String::trim)
                        .distinct()
                        .toList();
    }
}
