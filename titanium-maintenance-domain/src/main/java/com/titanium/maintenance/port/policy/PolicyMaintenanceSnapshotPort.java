package com.titanium.maintenance.port.policy;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.valueobject.casecreation.PolicyMaintenanceSnapshot;

/** 获取保全建案所需 Policy 权威基准快照的出口端口。 */
public interface PolicyMaintenanceSnapshotPort {

    /** 获取权威快照；任何不完整或不可验证结果都必须失败关闭。 */
    PolicyMaintenanceSnapshot capture(PolicyMaintenanceSnapshotRequest request);

    /** Policy 快照查询条件。 */
    record PolicyMaintenanceSnapshotRequest(String policyId, String tenantId) {

        public PolicyMaintenanceSnapshotRequest {
            policyId = requireText("policyId", policyId);
            tenantId = requireText("tenantId", tenantId);
        }

        private static String requireText(String fieldName, String value) {
            if (value == null || value.isBlank()) {
                throw new MaintenanceValidationException(
                        "PolicyMaintenanceSnapshotRequest", fieldName, "字段不能为空");
            }
            return value.trim();
        }
    }
}
