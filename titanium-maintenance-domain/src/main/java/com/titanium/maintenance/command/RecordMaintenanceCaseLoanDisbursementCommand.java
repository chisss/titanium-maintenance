package com.titanium.maintenance.command;

import java.util.List;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.workflow.MaintenanceLoanDisbursementEvidence;

/**
 * 以案件为单位原子记录保单贷款放款权威回执并完成全部生效任务（g04-03）。
 * <p>
 * 与 {@code RecordMaintenanceCasePolicyApplicationCommand}、{@code RecordMaintenanceCaseInvestmentSwitchCommand}
 * 并列的第三条生效回执入口：走保单贷款出口的案件由此命令收口，三者不得交叉使用（保单回执模型的不变量要求
 * 实际版本递增且字段非空，放款与账户转换两者皆不满足）。
 * </p>
 */
public record RecordMaintenanceCaseLoanDisbursementCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        List<String> taskIds,
        String operationId,
        MaintenanceLoanDisbursementEvidence evidence,
        String operatorId,
        String tenantId) {

    public RecordMaintenanceCaseLoanDisbursementCommand {
        taskIds = taskIds == null ? List.of() : List.copyOf(taskIds);
    }
}
