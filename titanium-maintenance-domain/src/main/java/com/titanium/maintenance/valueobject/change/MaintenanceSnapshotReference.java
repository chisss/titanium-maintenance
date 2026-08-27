package com.titanium.maintenance.valueobject.change;

import java.time.OffsetDateTime;
import java.util.regex.Pattern;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** 大体积保单快照的不可变引用。 */
public record MaintenanceSnapshotReference(String storageKey, String contentHash, long policyVersion,
        OffsetDateTime capturedAt) {

    private static final Pattern SHA_256 = Pattern.compile("[a-fA-F0-9]{64}");

    public MaintenanceSnapshotReference {
        if (storageKey == null || storageKey.isBlank()) {
            throw new MaintenanceValidationException(
                    "MaintenanceSnapshotReference", "storageKey", "快照存储键不能为空");
        }
        if (contentHash == null || !SHA_256.matcher(contentHash).matches()) {
            throw new MaintenanceValidationException(
                    "MaintenanceSnapshotReference", "contentHash", "快照摘要必须为 SHA-256 十六进制文本");
        }
        if (policyVersion < 0) {
            throw new MaintenanceValidationException(
                    "MaintenanceSnapshotReference", "policyVersion", "保单版本不能为负数");
        }
        if (capturedAt == null) {
            throw new MaintenanceValidationException(
                    "MaintenanceSnapshotReference", "capturedAt", "快照采集时间不能为空");
        }
        storageKey = storageKey.trim();
        contentHash = contentHash.toLowerCase();
    }
}
