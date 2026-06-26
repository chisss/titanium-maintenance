package com.titanium.maintenance.api.dto;

import com.titanium.maintenance.enums.EffectiveTimeType;
import com.titanium.maintenance.enums.MaintenanceStatus;
import com.titanium.maintenance.enums.MaintenanceType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class MaintenanceResponse {
    private String id;
    private String policyId;
    private String customerId;
    private MaintenanceType maintenanceType;
    private BigDecimal totalAmount;
    private BigDecimal refundAmount;
    private EffectiveTimeType effectiveTimeType;
    private LocalDateTime specificEffectiveDate;
    private String description;
    private MaintenanceStatus status;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
    private String tenantId;
}