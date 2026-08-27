package com.titanium.maintenance.web.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import com.titanium.maintenance.common.enums.workflow.MaintenanceReviewDecision;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 后台人工审核决定。 */
public record DecideMaintenanceReviewDTO(
        @NotBlank @Size(max = 128) String operationId,
        @NotNull MaintenanceReviewDecision decision,
        @NotBlank @Size(max = 64) String policyVersion,
        @NotBlank @Size(max = 500) String comment) {

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object ignoredValue) {
        throw new IllegalArgumentException("人工审核请求不支持字段: " + fieldName);
    }
}
