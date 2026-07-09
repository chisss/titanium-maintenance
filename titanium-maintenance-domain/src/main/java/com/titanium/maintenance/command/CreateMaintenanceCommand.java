package com.titanium.maintenance.command;

import java.time.LocalDateTime;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.valueobject.CustomerId;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.PolicyId;
import com.titanium.metadata.enums.maintenance.MaintenanceType;

/**
 * 创建保全命令（领域层）
 */
public record CreateMaintenanceCommand(@TargetAggregateIdentifier MaintenanceId id, PolicyId policyId,
        CustomerId customerId, MaintenanceType maintenanceType, EffectiveTimeType effectiveTimeType,
        LocalDateTime specificEffectiveDate, String description, String createdBy, String tenantId) {

    /**
     * 静态工厂：从外部原始参数构造命令，在边界处转换为强类型值对象。
     */
    public static CreateMaintenanceCommand of(String policyId, String customerId, MaintenanceType maintenanceType,
            EffectiveTimeType effectiveTimeType, LocalDateTime specificEffectiveDate, String description,
            String createdBy, String tenantId) {
        return new CreateMaintenanceCommand(MaintenanceId.generate(), PolicyId.of(policyId), CustomerId.of(customerId),
                maintenanceType, effectiveTimeType, specificEffectiveDate, description, createdBy, tenantId);
    }
}
