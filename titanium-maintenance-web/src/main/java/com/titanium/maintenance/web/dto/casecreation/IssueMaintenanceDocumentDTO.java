package com.titanium.maintenance.web.dto.casecreation;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 后台出具保全凭证的请求。
 * <p>
 * 凭证模板编码**不在请求中**：它由案件冻结配置的输出规则决定，由应用层读取后组装证据，
 * 调用方只能提供凭证编号，避免自报模板绕过配置权威。
 * </p>
 */
public record IssueMaintenanceDocumentDTO(
        @NotBlank @Size(max = 128) String operationId,
        @NotBlank @Size(max = 191) String voucherNo) {

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object ignoredValue) {
        throw new IllegalArgumentException("凭证出具请求不支持字段: " + fieldName);
    }
}
