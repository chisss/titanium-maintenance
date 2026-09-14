package com.titanium.maintenance.command;

import java.util.List;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.workflow.MaintenanceInvestmentSwitchEvidence;

/**
 * 以案件为单位原子记录投资账户转换权威回执并完成全部生效任务。
 * <p>
 * 与 {@code RecordMaintenanceCasePolicyApplicationCommand} 并列的第二条生效回执入口：走投资账户转换出口的案件
 * 由此命令收口，二者不得交叉使用（保单回执模型的不变量要求实际版本递增且字段非空，账户转换两者皆不满足）。
 * </p>
 */
public record RecordMaintenanceCaseInvestmentSwitchCommand(
        @TargetAggregateIdentifier MaintenanceId id,
        List<String> taskIds,
        String operationId,
        MaintenanceInvestmentSwitchEvidence evidence,
        String operatorId,
        String tenantId) {

    public RecordMaintenanceCaseInvestmentSwitchCommand {
        taskIds = taskIds == null ? List.of() : List.copyOf(taskIds);
    }
}
