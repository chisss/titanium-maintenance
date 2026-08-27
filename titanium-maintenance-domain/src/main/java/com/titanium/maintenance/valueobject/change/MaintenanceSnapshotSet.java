package com.titanium.maintenance.valueobject.change;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** 保全案件的变更前、拟变更和实际生效三快照引用。 */
public record MaintenanceSnapshotSet(MaintenanceSnapshotReference beforeSnapshot,
        MaintenanceSnapshotReference proposedSnapshot, MaintenanceSnapshotReference appliedSnapshot) {

    public MaintenanceSnapshotSet {
        if (beforeSnapshot == null) {
            throw new MaintenanceValidationException(
                    "MaintenanceSnapshotSet", "beforeSnapshot", "变更前快照不能为空");
        }
        validateVersion("proposedSnapshot", beforeSnapshot, proposedSnapshot);
        validateVersion("appliedSnapshot", beforeSnapshot, appliedSnapshot);
        if (appliedSnapshot != null && proposedSnapshot == null) {
            throw new MaintenanceValidationException(
                    "MaintenanceSnapshotSet", "appliedSnapshot", "记录实际生效快照前必须存在拟变更快照");
        }
    }

    public static MaintenanceSnapshotSet capturedBefore(MaintenanceSnapshotReference beforeSnapshot) {
        return new MaintenanceSnapshotSet(beforeSnapshot, null, null);
    }

    public MaintenanceSnapshotSet attachProposed(MaintenanceSnapshotReference proposedSnapshot) {
        return new MaintenanceSnapshotSet(beforeSnapshot, proposedSnapshot, null);
    }

    public MaintenanceSnapshotSet attachApplied(MaintenanceSnapshotReference appliedSnapshot) {
        if (proposedSnapshot == null) {
            throw new MaintenanceValidationException(
                    "MaintenanceSnapshotSet", "appliedSnapshot", "尚未记录拟变更快照");
        }
        return new MaintenanceSnapshotSet(beforeSnapshot, proposedSnapshot, appliedSnapshot);
    }

    private static void validateVersion(String fieldName, MaintenanceSnapshotReference before,
            MaintenanceSnapshotReference target) {
        if (target != null && target.policyVersion() < before.policyVersion()) {
            throw new MaintenanceValidationException(
                    "MaintenanceSnapshotSet", fieldName, "快照保单版本不能早于变更前版本");
        }
    }
}
