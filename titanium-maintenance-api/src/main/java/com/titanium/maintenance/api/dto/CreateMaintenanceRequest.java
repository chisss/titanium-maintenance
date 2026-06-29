package com.titanium.maintenance.api.dto;

import java.time.LocalDateTime;

import com.titanium.maintenance.enums.EffectiveTimeType;
import com.titanium.maintenance.enums.MaintenanceType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateMaintenanceRequest {
    @NotBlank(message = "Policy ID is required")
    @Size(max = 36, message = "Policy ID must be less than or equal to 36 characters")
    private String policyId;

    @NotBlank(message = "Customer ID is required")
    @Size(max = 36, message = "Customer ID must be less than or equal to 36 characters")
    private String customerId;

    @NotNull(message = "Maintenance type is required")
    private MaintenanceType maintenanceType;

    @NotNull(message = "Effective time type is required")
    private EffectiveTimeType effectiveTimeType;

    private LocalDateTime specificEffectiveDate;

    @Size(max = 500, message = "Description must be less than or equal to 500 characters")
    private String description;

    @NotBlank(message = "Created by is required")
    @Size(max = 50, message = "Created by must be less than or equal to 50 characters")
    private String createdBy;
}
