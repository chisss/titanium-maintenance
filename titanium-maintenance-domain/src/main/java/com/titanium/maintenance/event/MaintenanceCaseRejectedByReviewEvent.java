package com.titanium.maintenance.event;

import java.time.LocalDateTime;

import com.titanium.maintenance.valueobject.MaintenanceId;

/** 审核拒绝已经终止整个保全案件。 */
public record MaintenanceCaseRejectedByReviewEvent(
        MaintenanceId maintenanceId,
        String taskId,
        String reviewEvidenceHash,
        String policyCode,
        String policyVersion,
        String reason,
        LocalDateTime rejectedAt,
        String rejectedBy,
        String tenantId) {
}
