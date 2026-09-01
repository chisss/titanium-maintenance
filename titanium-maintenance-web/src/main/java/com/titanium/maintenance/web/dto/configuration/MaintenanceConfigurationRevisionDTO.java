package com.titanium.maintenance.web.dto.configuration;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 从已发布或退役配置创建修订草稿的入参。 */
public record MaintenanceConfigurationRevisionDTO(
        @NotBlank @Size(max = 64) String version,
        @NotNull LocalDateTime validFrom,
        LocalDateTime validTo) {
}
