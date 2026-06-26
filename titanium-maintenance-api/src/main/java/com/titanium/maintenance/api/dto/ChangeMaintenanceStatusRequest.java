package com.titanium.maintenance.api.dto;

import com.titanium.maintenance.enums.MaintenanceStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangeMaintenanceStatusRequest {
    @NotNull(message = "New status is required")
    private MaintenanceStatus newStatus;

    @Size(max = 500, message = "Change reason must be less than or equal to 500 characters")
    private String changeReason;

    @NotBlank(message = "Changed by is required")
    @Size(max = 50, message = "Changed by must be less than or equal to 50 characters")
    private String changedBy;
}