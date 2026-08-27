package com.titanium.maintenance.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 追溯期间重算请求；分析版本、期间和金额均由服务端权威事实派生。 */
public record RecalculateMaintenanceRetroactivePeriodsDTO(
        @NotBlank @Size(max = 128) String operationId) {
}
