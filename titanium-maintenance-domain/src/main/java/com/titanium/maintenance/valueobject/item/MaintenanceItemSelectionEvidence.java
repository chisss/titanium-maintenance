package com.titanium.maintenance.valueobject.item;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** 案件项目冻结的 Maintenance 配置与 Product Offering 权威证据。 */
public record MaintenanceItemSelectionEvidence(
        String configurationId,
        String configurationVersion,
        String configurationContentHash,
        String offeringId,
        String offeringVersion,
        String offeringContentHash,
        OffsetDateTime resolvedAt) {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public MaintenanceItemSelectionEvidence {
        configurationVersion = requireText("configurationVersion", configurationVersion);
        boolean legacy = configurationId == null && configurationContentHash == null
                && offeringId == null && offeringVersion == null
                && offeringContentHash == null && resolvedAt == null;
        if (!legacy) {
            configurationId = requireText("configurationId", configurationId);
            configurationContentHash = requireHash("configurationContentHash", configurationContentHash);
            offeringId = requireText("offeringId", offeringId);
            offeringVersion = requireText("offeringVersion", offeringVersion);
            offeringContentHash = requireHash("offeringContentHash", offeringContentHash);
            if (resolvedAt == null) {
                throw validation("resolvedAt", "证据解析时间不能为空");
            }
        }
    }

    public static MaintenanceItemSelectionEvidence authoritative(
            String configurationId,
            String configurationVersion,
            String configurationContentHash,
            String offeringId,
            String offeringVersion,
            String offeringContentHash,
            OffsetDateTime resolvedAt) {
        return new MaintenanceItemSelectionEvidence(
                configurationId, configurationVersion, configurationContentHash,
                offeringId, offeringVersion, offeringContentHash, resolvedAt);
    }

    /** 仅用于兼容 M3-04 前的历史项目事件，不代表权威配置证据。 */
    public static MaintenanceItemSelectionEvidence legacy(String configurationVersion) {
        return new MaintenanceItemSelectionEvidence(
                null, configurationVersion, null, null, null, null, null);
    }

    public boolean authoritative() {
        return configurationId != null;
    }

    /** 解析时间不同不改变已冻结的配置与 Offering 业务证据。 */
    public boolean sameAuthoritativeSelection(MaintenanceItemSelectionEvidence other) {
        if (other == null) {
            return false;
        }
        return Objects.equals(configurationId, other.configurationId)
                && Objects.equals(configurationVersion, other.configurationVersion)
                && Objects.equals(configurationContentHash, other.configurationContentHash)
                && Objects.equals(offeringId, other.offeringId)
                && Objects.equals(offeringVersion, other.offeringVersion)
                && Objects.equals(offeringContentHash, other.offeringContentHash);
    }

    private static String requireText(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw validation(fieldName, "字段不能为空");
        }
        return value.trim();
    }

    private static String requireHash(String fieldName, String value) {
        String hash = requireText(fieldName, value).toLowerCase(Locale.ROOT);
        if (!SHA_256.matcher(hash).matches()) {
            throw validation(fieldName, "内容摘要必须为 SHA-256 十六进制文本");
        }
        return hash;
    }

    private static MaintenanceValidationException validation(String fieldName, String message) {
        return new MaintenanceValidationException("MaintenanceItemSelectionEvidence", fieldName, message);
    }
}
