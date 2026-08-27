package com.titanium.maintenance.query.query;

import java.time.LocalDateTime;

import com.titanium.maintenance.common.enums.MaintenanceStatus;
import com.titanium.maintenance.common.enums.config.MaintenanceChannel;

/** 独立保全案件后台列表查询条件。 */
public record MaintenanceCaseSearchCriteria(
        String maintenanceId,
        String policyNumber,
        String customerId,
        String itemCode,
        MaintenanceChannel source,
        MaintenanceStatus status,
        String operatorId,
        LocalDateTime createdFrom,
        LocalDateTime createdTo,
        int page,
        int size) {
}
