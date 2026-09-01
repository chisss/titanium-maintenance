package com.titanium.maintenance.web.response.retroactive;

import java.time.LocalDateTime;

import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactAnalysisStatus;

/** 追溯影响分析操作结果。 */
public record MaintenanceRetroactiveImpactAnalysisVO(
        String analysisId,
        int analysisVersion,
        String operationId,
        MaintenanceRetroactiveImpactAnalysisStatus status,
        LocalDateTime scopeFrom,
        LocalDateTime scopeTo,
        int itemCount,
        int blockingItemCount,
        int pendingItemCount,
        String resultHash,
        String failureCode,
        String failureMessage,
        LocalDateTime completedAt) {
}
