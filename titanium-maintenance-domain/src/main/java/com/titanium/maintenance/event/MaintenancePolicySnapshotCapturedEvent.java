package com.titanium.maintenance.event;

import java.time.LocalDateTime;

import org.axonframework.serialization.Revision;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.casecreation.PolicyMaintenanceSnapshot;

/** 独立建案冻结 Policy 变更前快照的追加事实。 */
@Revision("1")
public record MaintenancePolicySnapshotCapturedEvent(
        MaintenanceId maintenanceId,
        PolicyMaintenanceSnapshot snapshot,
        LocalDateTime recordedAt,
        String recordedBy,
        String tenantId) {
}
