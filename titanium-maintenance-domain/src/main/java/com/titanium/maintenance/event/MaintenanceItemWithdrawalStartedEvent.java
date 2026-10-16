package com.titanium.maintenance.event;

import org.axonframework.serialization.Revision;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.withdrawal.MaintenanceItemWithdrawal;

/** 单个保全项目撤销请求已冻结。 */
@Revision("1")
public record MaintenanceItemWithdrawalStartedEvent(
        MaintenanceId maintenanceId,
        MaintenanceItemWithdrawal withdrawal,
        String tenantId) {
}
