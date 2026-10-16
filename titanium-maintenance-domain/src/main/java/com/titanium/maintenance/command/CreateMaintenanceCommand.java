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
     * <p>
     * 保全 ID 由调用方（application 边界）经 {@code IdGenerator} 生成后传入——ID 生成属边界职责，
     * 值对象不自产 ID（DSN-05 / ADR-0002）。
     * </p>
     */
    public static CreateMaintenanceCommand of(String maintenanceId, String policyId, String customerId,
            MaintenanceType maintenanceType, EffectiveTimeType effectiveTimeType, LocalDateTime specificEffectiveDate,
            String description, String createdBy, String tenantId) {
        return new CreateMaintenanceCommand(MaintenanceId.of(maintenanceId), PolicyId.of(policyId),
                CustomerId.of(customerId), maintenanceType, effectiveTimeType, specificEffectiveDate, description,
                createdBy, tenantId);
    }
}
