package com.titanium.maintenance.command;

import java.util.List;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.workflow.MaintenanceLoanRepaymentEvidence;

/**
 * 以案件为单位原子记录保单贷款还款权威回执并完成全部生效任务（g04-04）。
 * <p>
 * 与 {@code RecordMaintenanceCasePolicyApplicationCommand}、{@code RecordMaintenanceCaseInvestmentSwitchCommand}、
 * {@code RecordMaintenanceCaseLoanDisbursementCommand} 并列的第四条生效回执入口：走保单贷款还款出口的案件由此
 * 命令收口，四者不得交叉使用（保单回执模型的不变量要求实际版本递增且字段非空，还款与账户转换、放款三者皆不满足）。
 * </p>
 */
public record RecordMaintenanceCaseLoanRepaymentCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        List<String> taskIds,
        String operationId,
        MaintenanceLoanRepaymentEvidence evidence,
        String operatorId,
        String tenantId) {

    public RecordMaintenanceCaseLoanRepaymentCommand {
        taskIds = taskIds == null ? List.of() : List.copyOf(taskIds);
    }
}
