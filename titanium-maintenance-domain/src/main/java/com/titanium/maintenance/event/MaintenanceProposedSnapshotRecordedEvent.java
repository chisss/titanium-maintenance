package com.titanium.maintenance.event;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldCatalogSnapshot;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldValue;
import com.titanium.maintenance.valueobject.change.MaintenanceSnapshotReference;

/** 字段提案对应的完整拟变更快照与目录证据已记录。 */
public record MaintenanceProposedSnapshotRecordedEvent(
        MaintenanceId maintenanceId,
        String itemCode,
        MaintenanceSnapshotReference proposedSnapshot,
        Map<String, MaintenanceFieldValue> proposedFieldValues,
        MaintenanceFieldCatalogSnapshot fieldCatalogSnapshot,
        OffsetDateTime recordedAt,
        String recordedBy,
        String tenantId) {

    public MaintenanceProposedSnapshotRecordedEvent {
        proposedFieldValues = Collections.unmodifiableMap(new TreeMap<>(proposedFieldValues));
    }
}
