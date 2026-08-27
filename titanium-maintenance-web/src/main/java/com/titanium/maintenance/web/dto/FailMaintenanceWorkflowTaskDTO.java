package com.titanium.maintenance.web.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 记录任务失败的请求。 */
public record FailMaintenanceWorkflowTaskDTO(
        @NotBlank @Size(max = 128) String operationId,
        @NotBlank @Size(max = 64) String failureCode,
        @NotBlank @Size(max = 500) String failureReason) {

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object ignoredValue) {
        throw new IllegalArgumentException("任务失败请求不支持字段: " + fieldName);
    }
}
