package com.titanium.maintenance.application.command.casecreation;

import java.time.LocalDateTime;
import java.util.List;

import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;
import com.titanium.metadata.enums.maintenance.MaintenanceType;

/** 独立保全建案写入口参数。 */
public record CreateMaintenanceCaseInput(
        String policyId,
        List<String> itemCodes,
        EffectiveTimeType effectiveTimeType,
        LocalDateTime specificEffectiveDate,
        String description,
        String clientRequestKey,
        MaintenanceChannel source,
        String operatorId,
        String tenantId) {

    /** 兼容旧单保全类型入口。 */
    public CreateMaintenanceCaseInput(
            String policyId,
            MaintenanceType maintenanceType,
            EffectiveTimeType effectiveTimeType,
            LocalDateTime specificEffectiveDate,
            String description,
            String clientRequestKey,
            MaintenanceChannel source,
            String operatorId,
            String tenantId) {
        this(policyId, maintenanceType == null ? List.of() : List.of(maintenanceType.getCode()),
                effectiveTimeType, specificEffectiveDate, description, clientRequestKey,
                source, operatorId, tenantId);
    }
}
