package com.titanium.maintenance.web.dto.field;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 刷新案件字段冲突请求。 */
public record RefreshMaintenanceFieldConflictsDTO(
        @NotBlank @Size(max = 128) String operationId) {

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object value) {
        throw new IllegalArgumentException("未知请求字段: " + fieldName);
    }
}
