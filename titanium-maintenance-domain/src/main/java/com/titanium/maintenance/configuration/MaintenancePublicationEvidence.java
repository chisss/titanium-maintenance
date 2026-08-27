package com.titanium.maintenance.configuration;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.regex.Pattern;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** 发布配置时冻结的 Policy 字段目录证据。 */
public record MaintenancePublicationEvidence(String catalogVersion, String catalogHash,
        LocalDateTime validatedAt) {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public MaintenancePublicationEvidence {
        if (catalogVersion == null || catalogVersion.isBlank()) {
            throw validation("catalogVersion", "字段目录版本不能为空");
        }
        catalogVersion = catalogVersion.trim();
        if (catalogHash == null || !SHA_256.matcher(catalogHash.trim().toLowerCase(Locale.ROOT)).matches()) {
            throw validation("catalogHash", "字段目录哈希必须为 SHA-256");
        }
        catalogHash = catalogHash.trim().toLowerCase(Locale.ROOT);
        if (validatedAt == null) {
            throw validation("validatedAt", "发布校验时间不能为空");
        }
    }

    private static MaintenanceValidationException validation(String fieldName, String message) {
        return new MaintenanceValidationException("MaintenancePublicationEvidence", fieldName, message);
    }
}
