package com.titanium.maintenance.web.dto.configuration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 驳回或退回草稿的操作原因。 */
public record MaintenanceConfigurationDecisionDTO(
        @NotBlank @Size(max = 1000) String reason) {
}
