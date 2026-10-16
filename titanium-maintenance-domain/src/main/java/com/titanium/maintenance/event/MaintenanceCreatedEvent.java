package com.titanium.maintenance.event;

import java.time.LocalDateTime;

import org.axonframework.serialization.Revision;

import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.maintenance.valueobject.CustomerId;
import com.titanium.maintenance.valueobject.MaintenanceId;
import com.titanium.maintenance.valueobject.PolicyId;
import com.titanium.metadata.enums.maintenance.MaintenanceType;

@Revision("1")
public record MaintenanceCreatedEvent(MaintenanceId maintenanceId, PolicyId policyId, CustomerId customerId,
                                      MaintenanceType maintenanceType, EffectiveTimeType effectiveTimeType,
                                      LocalDateTime specificEffectiveDate, String description, LocalDateTime createdAt,
                                      String createdBy, String tenantId) {
}
