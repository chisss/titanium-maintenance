package com.titanium.maintenance.web.dto.retroactive;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 追溯影响分析请求；范围和案件事实由后台冻结。 */
public record AnalyzeMaintenanceRetroactiveImpactDTO(
        @NotBlank @Size(max = 128) String operationId) {
}
