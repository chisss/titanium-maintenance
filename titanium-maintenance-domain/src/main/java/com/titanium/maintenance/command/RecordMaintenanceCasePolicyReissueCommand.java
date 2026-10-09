package com.titanium.maintenance.command;

import java.util.List;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.workflow.MaintenancePolicyReissueEvidence;

/**
 * 以案件为单位原子记录保单补发单证请求回执并完成全部生效任务（g04-09）。
 * <p>
 * 与 {@code RecordMaintenanceCasePolicyApplicationCommand}、{@code RecordMaintenanceCaseInvestmentSwitchCommand}、
 * {@code RecordMaintenanceCaseLoanDisbursementCommand}、{@code RecordMaintenanceCaseLoanRepaymentCommand}
 * 并列的第五条生效回执入口：走保单补发出口的案件由此命令收口。五条路径的操作动作与回执证据类型均不同，
 * 不得交叉使用（保单回执模型的不变量要求实际版本递增且字段非空，补发三条皆不满足）。
 * </p>
 */
public record RecordMaintenanceCasePolicyReissueCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        List<String> taskIds,
        String operationId,
        MaintenancePolicyReissueEvidence evidence,
        String operatorId,
        String tenantId) {

    public RecordMaintenanceCasePolicyReissueCommand {
        taskIds = taskIds == null ? List.of() : List.copyOf(taskIds);
    }
}
