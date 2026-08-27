package com.titanium.maintenance.event;

import java.time.LocalDateTime;
import java.util.List;

import com.titanium.maintenance.valueobject.MaintenanceId;

/** 独立建案的项目配置与证据已经全部冻结，可进入后续信息录入。 */
public record MaintenanceCaseInitializationCompletedEvent(
        MaintenanceId maintenanceId,
        List<String> itemCodes,
        LocalDateTime completedAt,
        String completedBy,
        String tenantId) {

    public MaintenanceCaseInitializationCompletedEvent {
        itemCodes = List.copyOf(itemCodes);
    }
}
