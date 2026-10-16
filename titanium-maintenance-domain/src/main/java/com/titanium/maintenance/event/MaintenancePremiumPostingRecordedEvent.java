package com.titanium.maintenance.event;

import java.time.LocalDateTime;

import org.axonframework.serialization.Revision;

import com.titanium.maintenance.valueobject.MaintenanceId;

/** Billing 保全余额事实已记录。 */
@Revision("1")
public record MaintenancePremiumPostingRecordedEvent(
        MaintenanceId maintenanceId,
        String adjustmentId,
        String adjustmentResultHash,
        String postingId,
        String postingStatus,
        LocalDateTime recordedAt,
        String updatedBy,
        String tenantId) {
}
