package com.titanium.maintenance.common.exception;

import com.titanium.maintenance.common.enums.PolicyMaintenanceSnapshotFailureReason;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.exception.DomainException;

import lombok.Getter;

/** Policy 建案快照不存在、不适用或权威证据不可用。 */
@Getter
public class PolicyMaintenanceSnapshotException extends DomainException {

    private final PolicyMaintenanceSnapshotFailureReason reason;

    public PolicyMaintenanceSnapshotException(
            PolicyMaintenanceSnapshotFailureReason reason,
            String message) {
        super(errorCode(reason), message);
        this.reason = requireReason(reason);
    }

    public PolicyMaintenanceSnapshotException(
            PolicyMaintenanceSnapshotFailureReason reason,
            String message,
            Throwable cause) {
        super(errorCode(reason), message, cause);
        this.reason = requireReason(reason);
    }

    /** 失败原因 → 标准错误码枚举（71 段）映射。 */
    private static MaintenanceErrorCode errorCode(PolicyMaintenanceSnapshotFailureReason reason) {
        return switch (requireReason(reason)) {
            case NOT_FOUND -> MaintenanceErrorCode.MAINTENANCE_POLICY_SNAPSHOT_NOT_FOUND;
            case INACTIVE -> MaintenanceErrorCode.MAINTENANCE_POLICY_SNAPSHOT_INACTIVE;
            case TENANT_MISMATCH -> MaintenanceErrorCode.MAINTENANCE_POLICY_SNAPSHOT_TENANT_MISMATCH;
            case VERSION_MISSING -> MaintenanceErrorCode.MAINTENANCE_POLICY_SNAPSHOT_VERSION_MISSING;
            case CONTRACT_INVALID -> MaintenanceErrorCode.MAINTENANCE_POLICY_SNAPSHOT_CONTRACT_INVALID;
            case UNAVAILABLE -> MaintenanceErrorCode.MAINTENANCE_POLICY_SNAPSHOT_UNAVAILABLE;
        };
    }

    private static PolicyMaintenanceSnapshotFailureReason requireReason(
            PolicyMaintenanceSnapshotFailureReason reason) {
        if (reason == null) {
            throw new IllegalArgumentException("Policy建案快照失败原因不能为空");
        }
        return reason;
    }
}
