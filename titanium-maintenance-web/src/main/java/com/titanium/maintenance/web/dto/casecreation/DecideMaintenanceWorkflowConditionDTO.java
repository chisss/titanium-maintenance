package com.titanium.maintenance.web.dto.casecreation;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import com.titanium.maintenance.common.enums.workflow.MaintenanceWorkflowConditionDecision;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 权威规则调用方提交条件任务结论。 */
public record DecideMaintenanceWorkflowConditionDTO(
        @NotBlank @Size(max = 128) String operationId,
        @NotBlank @Size(max = 64) String ruleVersion,
        @NotBlank @Pattern(regexp = "[0-9a-fA-F]{64}") String inputHash,
        @NotNull MaintenanceWorkflowConditionDecision decision,
        @NotBlank @Size(max = 500) String reason) {

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object ignoredValue) {
        throw new IllegalArgumentException("条件判定请求不支持字段: " + fieldName);
    }
}
