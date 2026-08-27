package com.titanium.maintenance.event;

import java.time.LocalDateTime;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.casecreation.PolicyMaintenanceSnapshot;

/** 独立建案冻结 Policy 变更前快照的追加事实。 */
public record MaintenancePolicySnapshotCapturedEvent(
        MaintenanceId maintenanceId,
        PolicyMaintenanceSnapshot snapshot,
        LocalDateTime recordedAt,
        String recordedBy,
        String tenantId) {
}
