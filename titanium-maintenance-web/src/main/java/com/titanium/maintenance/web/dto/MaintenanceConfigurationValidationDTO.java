package com.titanium.maintenance.web.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 权威字段目录与外部引用校验条件。 */
public record MaintenanceConfigurationValidationDTO(
        @Size(max = 64) String productType,
        @Size(max = 64) String policyType,
        @NotNull LocalDate businessDate) {
}
