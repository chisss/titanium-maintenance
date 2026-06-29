package com.titanium.maintenance.event;

import java.time.LocalDateTime;

import com.titanium.maintenance.enums.EffectiveTimeType;
import com.titanium.maintenance.enums.MaintenanceType;
import com.titanium.maintenance.valueobject.CustomerId;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.PolicyId;

public record MaintenanceCreatedEvent(MaintenanceId maintenanceId, PolicyId policyId, CustomerId customerId,
                                      MaintenanceType maintenanceType, EffectiveTimeType effectiveTimeType,
                                      LocalDateTime specificEffectiveDate, String description, LocalDateTime createdAt,
                                      String createdBy, String tenantId) {
}
