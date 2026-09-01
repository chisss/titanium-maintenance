package com.titanium.maintenance.web.dto.effect;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 未来生效计划操作请求；日期和租户时区由权威案件事实解析。 */
public record MaintenanceEffectScheduleOperationDTO(
        @NotBlank @Size(max = 128) String operationId,
        @Size(max = 500) String reason) {
}
