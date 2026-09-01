package com.titanium.maintenance.web.dto.casecreation;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 恢复失败任务的请求。 */
public record RetryMaintenanceWorkflowTaskDTO(
        @NotBlank @Size(max = 128) String operationId,
        @NotBlank @Size(max = 500) String reason) {

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object ignoredValue) {
        throw new IllegalArgumentException("任务重试请求不支持字段: " + fieldName);
    }
}
