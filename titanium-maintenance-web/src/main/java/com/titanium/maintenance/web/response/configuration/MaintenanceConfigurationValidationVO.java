package com.titanium.maintenance.web.response.configuration;

import java.time.LocalDateTime;
import java.util.List;

/** 配置权威校验结果。 */
public record MaintenanceConfigurationValidationVO(
        boolean valid,
        List<IssueVO> issues,
        String catalogVersion,
        String catalogHash,
        String referenceEvidenceVersion,
        LocalDateTime validatedAt) {

    public record IssueVO(String code, String field, String message) {
    }
}
