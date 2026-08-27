package com.titanium.maintenance.port;

import java.time.LocalDateTime;
import java.util.List;

/** 项目撤销失败恢复租约端口；实现必须以数据库条件更新保证多节点互斥。 */
public interface MaintenanceItemWithdrawalRecoveryLeasePort {

    List<WithdrawalRecoveryLease> acquireDue(
            String leaseOwner,
            LocalDateTime now,
            LocalDateTime retryBefore,
            LocalDateTime leaseUntil,
            int batchSize,
            int maxAttempts);

    void release(String itemViewId, String leaseOwner);

    record WithdrawalRecoveryLease(
            String itemViewId,
            String maintenanceId,
            String tenantId,
            String itemCode,
            String operationId,
            String reason,
            String paymentMethod,
            int retryCount) {
    }
}
