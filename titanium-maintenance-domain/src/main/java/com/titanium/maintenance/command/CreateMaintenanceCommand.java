package com.titanium.maintenance.command;

import java.time.LocalDateTime;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.maintenance.enums.EffectiveTimeType;
import com.titanium.maintenance.enums.MaintenanceType;
import com.titanium.maintenance.valueobject.CustomerId;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.PolicyId;

import lombok.Builder;
import lombok.Value;

/**
 * 创建保全命令（领域层）
 */
@Value
@Builder
public class CreateMaintenanceCommand {
    @TargetAggregateIdentifier
    MaintenanceId     id;
    PolicyId          policyId;
    CustomerId        customerId;
    MaintenanceType   maintenanceType;
    EffectiveTimeType effectiveTimeType;
    LocalDateTime     specificEffectiveDate;
    String            description;
    String            createdBy;
    String            tenantId;

    // 静态工厂方法，方便创建命令
    public static CreateMaintenanceCommand of(String policyId, String customerId, MaintenanceType maintenanceType,
                                              EffectiveTimeType effectiveTimeType, LocalDateTime specificEffectiveDate,
                                              String description, String createdBy, String tenantId) {
        return CreateMaintenanceCommand.builder().id(MaintenanceId.generate()).policyId(PolicyId.of(policyId))
                .customerId(CustomerId.of(customerId)).maintenanceType(maintenanceType)
                .effectiveTimeType(effectiveTimeType).specificEffectiveDate(specificEffectiveDate)
                .description(description).createdBy(createdBy).tenantId(tenantId).build();
    }
}
