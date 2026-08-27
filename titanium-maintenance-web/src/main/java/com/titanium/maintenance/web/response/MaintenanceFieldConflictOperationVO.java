package com.titanium.maintenance.web.response;

/** 冲突刷新或解决响应。 */
public record MaintenanceFieldConflictOperationVO(
        String operationId,
        long policyVersion,
        String proposedSnapshotHash,
        int conflictCount) {
}
