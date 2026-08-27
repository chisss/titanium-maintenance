package com.titanium.maintenance.event;

import java.time.LocalDateTime;
import java.util.List;

import com.titanium.maintenance.valueobject.MaintenanceId;

/** 独立建案已记录本次请求计划冻结的全部保全项。 */
public record MaintenanceCaseItemsPlannedEvent(
        MaintenanceId maintenanceId,
        List<String> itemCodes,
        LocalDateTime plannedAt,
        String plannedBy,
        String tenantId) {

    public MaintenanceCaseItemsPlannedEvent {
        itemCodes = List.copyOf(itemCodes);
    }
}
