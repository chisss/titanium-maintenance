package com.titanium.maintenance.application.model.configuration;

import java.time.LocalDateTime;
import java.util.List;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** 保全项配置针对权威目录和引用注册表的完整校验结果。 */
public record MaintenanceConfigurationValidationResult(boolean valid, List<ValidationIssue> issues,
        String catalogVersion, String catalogHash, String referenceEvidenceVersion,
        LocalDateTime validatedAt) {

    public MaintenanceConfigurationValidationResult {
        issues = issues == null ? List.of() : List.copyOf(issues);
        if (issues.stream().anyMatch(issue -> issue == null)) {
            throw validation("issues", "校验问题不能包含空项");
        }
        catalogVersion = requireText("catalogVersion", catalogVersion);
        catalogHash = requireText("catalogHash", catalogHash);
        referenceEvidenceVersion = requireText("referenceEvidenceVersion", referenceEvidenceVersion);
        if (validatedAt == null) {
            throw validation("validatedAt", "校验时间不能为空");
        }
        if (valid == !issues.isEmpty()) {
            throw validation("valid", "校验状态必须与问题列表一致");
        }
    }

    /** 可稳定返回给管理端的配置问题。 */
    public record ValidationIssue(String code, String field, String message) {

        public ValidationIssue {
            code = requireText("code", code);
            field = requireText("field", field);
            message = requireText("message", message);
        }
    }

    private static String requireText(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw validation(fieldName, "字段不能为空");
        }
        return value.trim();
    }

    private static MaintenanceValidationException validation(String fieldName, String message) {
        return new MaintenanceValidationException("MaintenanceConfigurationValidationResult", fieldName, message);
    }
}
