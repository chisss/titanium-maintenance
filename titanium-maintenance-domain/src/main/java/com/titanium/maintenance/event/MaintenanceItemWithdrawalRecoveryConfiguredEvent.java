package com.titanium.maintenance.event;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.withdrawal.MaintenanceItemWithdrawalRecoveryContext;

/** 项目撤销自动恢复上下文已冻结。 */
public record MaintenanceItemWithdrawalRecoveryConfiguredEvent(
        MaintenanceId maintenanceId,
        MaintenanceItemWithdrawalRecoveryContext recoveryContext,
        String tenantId) {
}
