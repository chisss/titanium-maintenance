package com.titanium.maintenance.event;

import java.time.LocalDateTime;

import com.titanium.maintenance.valueobject.MaintenanceId;

/** 核保拒绝已经终止整个保全案件。 */
public record MaintenanceCaseRejectedByUnderwritingEvent(
        MaintenanceId maintenanceId,
        String taskId,
        String underwritingCaseId,
        String underwritingEvidenceHash,
        String ruleVersion,
        String modelVersion,
        String reason,
        LocalDateTime rejectedAt,
        String rejectedBy,
        String tenantId) {
}
