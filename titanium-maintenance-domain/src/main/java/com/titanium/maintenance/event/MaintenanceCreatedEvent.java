package com.titanium.maintenance.event;

import com.titanium.maintenance.enums.EffectiveTimeType;
import com.titanium.maintenance.enums.MaintenanceType;
import com.titanium.maintenance.valueobject.CustomerId;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.PolicyId;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@ToString
@AllArgsConstructor
public class MaintenanceCreatedEvent {
    private final MaintenanceId maintenanceId;
    private final PolicyId policyId;
    private final CustomerId customerId;
    private final MaintenanceType maintenanceType;
    private final EffectiveTimeType effectiveTimeType;
    private final LocalDateTime specificEffectiveDate;
    private final String description;
    private final LocalDateTime createdAt;
    private final String createdBy;
    private final String tenantId;
}