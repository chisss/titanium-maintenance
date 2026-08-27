package com.titanium.maintenance.event;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.withdrawal.MaintenanceItemWithdrawal;

/** 单个保全项目撤销请求已冻结。 */
public record MaintenanceItemWithdrawalStartedEvent(
        MaintenanceId maintenanceId,
        MaintenanceItemWithdrawal withdrawal,
        String tenantId) {
}
