package com.titanium.maintenance.application.command.effect;

import com.titanium.maintenance.application.model.effect.MaintenanceInvestmentSwitchInput;
import com.titanium.maintenance.application.model.effect.MaintenanceLoanDisbursementInput;
import com.titanium.maintenance.application.model.effect.MaintenanceLoanRepaymentInput;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;

/**
 * 人工/API 立即触发合同应用的应用层输入。
 *
 * @param investmentSwitch 账户转换类案件的转换参数；非账户转换案件为 {@code null}
 * @param loanDisbursement 保单贷款放款类案件的申请金额参数；非贷款放款案件为 {@code null}
 * @param loanRepayment 保单贷款还款类案件的目标明细与还款额参数；非还款案件为 {@code null}
 */
public record MaintenanceEffectApplicationInput(
        String maintenanceId,
        String taskId,
        String operationId,
        String operatorId,
        String tenantId,
        MaintenanceChannel source,
        MaintenanceInvestmentSwitchInput investmentSwitch,
        MaintenanceLoanDisbursementInput loanDisbursement,
        MaintenanceLoanRepaymentInput loanRepayment) {

    /** 兼容既有调用：普通字段型/状态型案件不携带出口专用参数。 */
    public MaintenanceEffectApplicationInput(
            String maintenanceId,
            String taskId,
            String operationId,
            String operatorId,
            String tenantId,
            MaintenanceChannel source) {
        this(maintenanceId, taskId, operationId, operatorId, tenantId, source, null, null, null);
    }

    /** 兼容既有调用：携带投资参数（账户转换案件）。 */
    public MaintenanceEffectApplicationInput(
            String maintenanceId,
            String taskId,
            String operationId,
            String operatorId,
            String tenantId,
            MaintenanceChannel source,
            MaintenanceInvestmentSwitchInput investmentSwitch) {
        this(maintenanceId, taskId, operationId, operatorId, tenantId, source, investmentSwitch, null, null);
    }

    /** 兼容既有调用：携带投资参数与贷款放款参数（由生效编排按案件类型二选一携带）。 */
    public MaintenanceEffectApplicationInput(
            String maintenanceId,
            String taskId,
            String operationId,
            String operatorId,
            String tenantId,
            MaintenanceChannel source,
            MaintenanceInvestmentSwitchInput investmentSwitch,
            MaintenanceLoanDisbursementInput loanDisbursement) {
        this(maintenanceId, taskId, operationId, operatorId, tenantId, source,
                investmentSwitch, loanDisbursement, null);
    }

    /** 紧凑构造器：出口专用参数三者互斥（由生效编排按案件类型分派，不得同时携带）。 */
    public MaintenanceEffectApplicationInput {
        int exits = (investmentSwitch == null ? 0 : 1)
                + (loanDisbursement == null ? 0 : 1)
                + (loanRepayment == null ? 0 : 1);
        if (exits > 1) {
            throw new IllegalArgumentException("账户转换、贷款放款与贷款还款参数不得同时携带");
        }
    }
}
