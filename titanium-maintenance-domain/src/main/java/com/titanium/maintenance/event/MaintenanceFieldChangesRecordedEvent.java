package com.titanium.maintenance.event;

import java.time.LocalDateTime;
import java.util.List;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldChange;

/** 某一保全项的完整字段提案已记录。 */
public record MaintenanceFieldChangesRecordedEvent(MaintenanceId maintenanceId, String itemCode,
        List<MaintenanceFieldChange> changes, LocalDateTime recordedAt, String updatedBy, String tenantId) {

    public MaintenanceFieldChangesRecordedEvent {
        changes = changes == null ? List.of() : List.copyOf(changes);
    }
}
