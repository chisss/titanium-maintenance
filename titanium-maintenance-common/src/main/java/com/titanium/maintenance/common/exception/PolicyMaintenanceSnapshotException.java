package com.titanium.maintenance.common.exception;

import com.titanium.maintenance.common.enums.PolicyMaintenanceSnapshotFailureReason;
import com.titanium.metadata.exception.DomainException;

import lombok.Getter;

/** Policy 建案快照不存在、不适用或权威证据不可用。 */
@Getter
public class PolicyMaintenanceSnapshotException extends DomainException {

    private static final String ERROR_CODE_PREFIX = "MAINTENANCE_POLICY_SNAPSHOT_";

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

    private static String errorCode(PolicyMaintenanceSnapshotFailureReason reason) {
        return ERROR_CODE_PREFIX + requireReason(reason).name();
    }

    private static PolicyMaintenanceSnapshotFailureReason requireReason(
            PolicyMaintenanceSnapshotFailureReason reason) {
        if (reason == null) {
            throw new IllegalArgumentException("Policy建案快照失败原因不能为空");
        }
        return reason;
    }
}
