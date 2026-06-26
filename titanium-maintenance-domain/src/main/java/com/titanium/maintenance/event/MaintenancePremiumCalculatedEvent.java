package com.titanium.maintenance.event;

import com.titanium.maintenance.valueobject.MaintenanceId;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@ToString
@AllArgsConstructor
public class MaintenancePremiumCalculatedEvent {
    private final MaintenanceId maintenanceId;
    private final BigDecimal totalAmount;
    private final BigDecimal refundAmount;
    private final String calculationDetails;
    private final LocalDateTime updatedAt;
    private final String updatedBy;
    private final String tenantId;
}