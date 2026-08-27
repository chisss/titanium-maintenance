package com.titanium.maintenance.valueobject.change;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** 字段冲突刷新或解决后的案件级拟快照计划。 */
public record MaintenanceFieldConflictPlan(
        Map<String, List<MaintenanceFieldChange>> changesByItem,
        Map<String, MaintenanceFieldValue> proposedFieldValues,
        MaintenanceSnapshotReference proposedSnapshot,
        int conflictCount) {

    public MaintenanceFieldConflictPlan {
        if (changesByItem == null || proposedFieldValues == null || proposedSnapshot == null || conflictCount < 0) {
            throw new MaintenanceValidationException(
                    "MaintenanceFieldConflictPlan", "plan", "字段冲突计划不完整");
        }
        Map<String, List<MaintenanceFieldChange>> immutableChanges = new LinkedHashMap<>();
        new TreeMap<>(changesByItem).forEach(
                (itemCode, changes) -> immutableChanges.put(itemCode, List.copyOf(changes)));
        changesByItem = Collections.unmodifiableMap(immutableChanges);
        proposedFieldValues = Collections.unmodifiableMap(new TreeMap<>(proposedFieldValues));
        long actualConflictCount = changesByItem.values().stream()
                .flatMap(List::stream)
                .filter(MaintenanceFieldChange::hasUnresolvedConflict)
                .count();
        if (actualConflictCount != conflictCount) {
            throw new MaintenanceValidationException(
                    "MaintenanceFieldConflictPlan", "conflictCount", "冲突数量与字段计划不一致");
        }
    }

    public List<MaintenanceFieldChange> allChanges() {
        return changesByItem.values().stream().flatMap(List::stream).toList();
    }
}
