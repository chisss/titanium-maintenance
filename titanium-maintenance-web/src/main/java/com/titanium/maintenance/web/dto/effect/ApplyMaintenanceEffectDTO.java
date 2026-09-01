package com.titanium.maintenance.web.dto.effect;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 人工/API 触发立即生效的请求。 */
public record ApplyMaintenanceEffectDTO(
        @NotBlank @Size(max = 128) String operationId) {
}
