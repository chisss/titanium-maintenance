package com.titanium.maintenance.application.command.effect;

import com.titanium.maintenance.application.model.effect.MaintenanceInvestmentSwitchInput;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;

/**
 * 人工/API 立即触发合同应用的应用层输入。
 *
 * @param investmentSwitch 账户转换类案件的转换参数；非账户转换案件为 {@code null}
 */
public record MaintenanceEffectApplicationInput(
        String maintenanceId,
        String taskId,
        String operationId,
        String operatorId,
        String tenantId,
        MaintenanceChannel source,
        MaintenanceInvestmentSwitchInput investmentSwitch) {

    /** 兼容既有调用：非账户转换案件不携带投资参数。 */
    public MaintenanceEffectApplicationInput(
            String maintenanceId,
            String taskId,
            String operationId,
            String operatorId,
            String tenantId,
            MaintenanceChannel source) {
        this(maintenanceId, taskId, operationId, operatorId, tenantId, source, null);
    }
}
