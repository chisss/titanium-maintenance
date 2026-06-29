package com.titanium.maintenance.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.maintenance.valueobject.MaintenanceId;

public record MaintenancePremiumCalculatedEvent(MaintenanceId maintenanceId, BigDecimal totalAmount,
                                                BigDecimal refundAmount, String calculationDetails,
                                                LocalDateTime updatedAt, String updatedBy, String tenantId) {
}
