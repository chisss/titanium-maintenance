package com.titanium.maintenance.web.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 完成信息录入或业务校验任务的请求。 */
public record CompleteMaintenanceWorkflowTaskDTO(
        @NotBlank @Size(max = 128) String operationId,
        @Size(max = 64) String evidenceVersion,
        @Pattern(regexp = "[0-9a-fA-F]{64}") String evidenceHash,
        @Size(max = 64) String resultCode,
        @Size(max = 500) String reason) {

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object ignoredValue) {
        throw new IllegalArgumentException("任务完成请求不支持字段: " + fieldName);
    }
}
