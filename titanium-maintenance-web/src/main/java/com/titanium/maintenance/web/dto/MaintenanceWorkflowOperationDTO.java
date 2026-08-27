package com.titanium.maintenance.web.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 领取或开始任务的最小操作请求。 */
public record MaintenanceWorkflowOperationDTO(
        @NotBlank @Size(max = 128) String operationId) {

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object ignoredValue) {
        throw new IllegalArgumentException("任务操作请求不支持字段: " + fieldName);
    }
}
