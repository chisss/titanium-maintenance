package com.titanium.maintenance.web.dto.casecreation;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 受信系统 API 提交的自动审核外部证据摘要。 */
public record AutomaticMaintenanceReviewDTO(
        @NotBlank @Size(max = 128) String operationId,
        @Size(max = 64) String policyVersion,
        Boolean identityVerified,
        @Pattern(regexp = "[0-9a-fA-F]{64}") String identityEvidenceHash,
        @Size(max = 100) List<@NotBlank @Size(max = 64) String> satisfiedMaterialCodes,
        @Pattern(regexp = "[0-9a-fA-F]{64}") String materialEvidenceHash,
        Boolean amountWithinLimit,
        @Pattern(regexp = "[0-9a-fA-F]{64}") String amountEvidenceHash,
        Boolean riskAccepted,
        @Pattern(regexp = "[0-9a-fA-F]{64}") String riskEvidenceHash) {

    public AutomaticMaintenanceReviewDTO {
        satisfiedMaterialCodes = satisfiedMaterialCodes == null
                ? List.of()
                : List.copyOf(satisfiedMaterialCodes);
    }

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object ignoredValue) {
        throw new IllegalArgumentException("自动审核请求不支持字段: " + fieldName);
    }
}
