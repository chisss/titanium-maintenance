package com.titanium.maintenance.web.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import com.titanium.maintenance.common.enums.change.MaintenanceFieldConflictResolutionAction;
import com.titanium.maintenance.common.enums.change.PolicyFieldDataType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 显式解决单个字段冲突请求。 */
public record ResolveMaintenanceFieldConflictDTO(
        @NotBlank @Size(max = 128) String operationId,
        @NotBlank @Size(max = 64) String itemCode,
        @NotBlank @Size(max = 128) String objectId,
        @NotBlank @Size(max = 128) String fieldCode,
        @NotNull MaintenanceFieldConflictResolutionAction action,
        PolicyFieldDataType dataType,
        @Size(max = 4000) String canonicalValue,
        @NotBlank @Size(max = 500) String reason) {

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object value) {
        throw new IllegalArgumentException("未知请求字段: " + fieldName);
    }
}
