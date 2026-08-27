package com.titanium.maintenance.valueobject.change;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 已校验字段差异与完整拟变更快照。 */
public record MaintenanceFieldProposalPlan(
        List<MaintenanceFieldChange> changes,
        Map<String, MaintenanceFieldValue> proposedFieldValues,
        MaintenanceSnapshotReference proposedSnapshot) {

    public MaintenanceFieldProposalPlan {
        changes = List.copyOf(changes);
        proposedFieldValues = Collections.unmodifiableMap(new TreeMap<>(proposedFieldValues));
    }
}
