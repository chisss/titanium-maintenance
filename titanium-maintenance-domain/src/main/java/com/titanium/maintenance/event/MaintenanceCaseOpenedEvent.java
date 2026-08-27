package com.titanium.maintenance.event;

import java.time.LocalDateTime;

import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.maintenance.valueobject.MaintenanceId;

/** 独立入口完成保全建案后追加的来源与幂等事实。 */
public record MaintenanceCaseOpenedEvent(
        MaintenanceId maintenanceId,
        MaintenanceChannel source,
        String clientRequestKey,
        String requestFingerprint,
        LocalDateTime openedAt,
        String openedBy,
        String tenantId) {
}
