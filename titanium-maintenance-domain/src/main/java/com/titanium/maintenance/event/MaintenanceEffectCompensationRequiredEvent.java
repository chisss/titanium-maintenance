package com.titanium.maintenance.event;

import java.time.LocalDateTime;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.workflow.MaintenanceEffectCompensationEvidence;

/** 案件需要人工重试勾稽 Policy 权威回执。 */
public record MaintenanceEffectCompensationRequiredEvent(
        MaintenanceId maintenanceId,
        String taskId,
        MaintenanceEffectCompensationEvidence evidence,
        LocalDateTime recordedAt,
        String recordedBy,
        String tenantId) {
}
