package com.titanium.maintenance.application.model;

import java.util.List;

import com.titanium.maintenance.valueobject.change.MaintenanceFieldChange;

/** 冲突刷新或解决后的案件权威状态。 */
public record MaintenanceFieldConflictOperationResult(
        String operationId,
        long policyVersion,
        String proposedSnapshotHash,
        int conflictCount,
        List<MaintenanceFieldChange> fieldChanges) {

    public MaintenanceFieldConflictOperationResult {
        fieldChanges = List.copyOf(fieldChanges);
    }
}
