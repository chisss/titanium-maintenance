package com.titanium.maintenance.infrastructure.adapter;

import com.titanium.maintenance.common.enums.PolicyMaintenanceSnapshotFailureReason;
import com.titanium.maintenance.common.exception.PolicyMaintenanceSnapshotException;
import com.titanium.maintenance.port.PolicyMaintenanceSnapshotPort;
import com.titanium.maintenance.valueobject.casecreation.PolicyMaintenanceSnapshot;

/** Policy 正式快照契约未接入时使用的失败关闭适配器。 */
public class UnavailablePolicyMaintenanceSnapshotAdapter implements PolicyMaintenanceSnapshotPort {

    @Override
    public PolicyMaintenanceSnapshot capture(PolicyMaintenanceSnapshotRequest request) {
        throw new PolicyMaintenanceSnapshotException(
                PolicyMaintenanceSnapshotFailureReason.UNAVAILABLE,
                "Policy权威建案快照API尚未提供完整版本与结构化快照证据");
    }
}
