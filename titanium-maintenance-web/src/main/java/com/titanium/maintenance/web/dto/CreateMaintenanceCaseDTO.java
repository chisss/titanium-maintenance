package com.titanium.maintenance.web.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import com.titanium.maintenance.common.enums.EffectiveTimeType;
import com.titanium.metadata.enums.maintenance.MaintenanceType;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 独立保全建案请求；客户与操作人身份不接受请求体自报。 */
public record CreateMaintenanceCaseDTO(
        @NotBlank @Size(max = 36) String policyId,
        MaintenanceType maintenanceType,
        @Size(min = 1, max = 10)
        List<@NotBlank @Size(max = 64) @Pattern(regexp = "[A-Z][A-Z0-9_]{2,63}") String> itemCodes,
        @NotNull EffectiveTimeType effectiveTimeType,
        LocalDateTime specificEffectiveDate,
        @Size(max = 500) String description,
        @NotBlank @Size(max = 128) String clientRequestKey) {

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object ignoredValue) {
        throw new IllegalArgumentException("建案请求不支持字段: " + fieldName);
    }

    @AssertTrue(message = "maintenanceType 与 itemCodes 必须且只能提供一种")
    public boolean isItemSelectionValid() {
        boolean hasLegacyType = maintenanceType != null;
        boolean hasItemCodes = itemCodes != null && !itemCodes.isEmpty();
        return hasLegacyType != hasItemCodes;
    }

    public List<String> resolvedItemCodes() {
        return itemCodes == null || itemCodes.isEmpty()
                ? List.of(maintenanceType.getCode())
                : List.copyOf(itemCodes);
    }
}
