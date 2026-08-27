package com.titanium.maintenance.valueobject.change;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** 字段草稿使用的 Policy 目录版本与已选字段描述快照。 */
public record MaintenanceFieldCatalogSnapshot(
        String tenantId,
        LocalDate businessDate,
        String catalogVersion,
        String catalogHash,
        OffsetDateTime capturedAt,
        Map<String, MaintenanceFieldDescriptorSnapshot> fields) {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public MaintenanceFieldCatalogSnapshot {
        tenantId = requireText("tenantId", tenantId);
        catalogVersion = requireText("catalogVersion", catalogVersion);
        catalogHash = requireText("catalogHash", catalogHash).toLowerCase();
        if (businessDate == null || capturedAt == null) {
            throw validation("businessDate", "目录业务日期和采集时间不能为空");
        }
        if (!SHA_256.matcher(catalogHash).matches()) {
            throw validation("catalogHash", "目录摘要必须为 SHA-256 十六进制文本");
        }
        if (fields == null || fields.isEmpty()) {
            throw validation("fields", "字段目录快照不能为空");
        }
        TreeMap<String, MaintenanceFieldDescriptorSnapshot> sorted = new TreeMap<>();
        fields.forEach((fieldCode, descriptor) -> {
            if (descriptor == null || !descriptor.fieldCode().equals(fieldCode)) {
                throw validation("fields", "字段目录键与描述不一致");
            }
            sorted.put(fieldCode, descriptor);
        });
        fields = Collections.unmodifiableMap(sorted);
    }

    public MaintenanceFieldDescriptorSnapshot requireField(String fieldCode) {
        MaintenanceFieldDescriptorSnapshot descriptor = fields.get(fieldCode);
        if (descriptor == null) {
            throw validation("fields", "字段目录不存在字段: " + fieldCode);
        }
        return descriptor;
    }

    /** 判断两次采集是否代表同一目录权威事实，忽略采集动作本身的时间。 */
    public boolean sameAuthorityAs(MaintenanceFieldCatalogSnapshot other) {
        return other != null
                && tenantId.equals(other.tenantId)
                && businessDate.equals(other.businessDate)
                && catalogVersion.equals(other.catalogVersion)
                && catalogHash.equals(other.catalogHash)
                && fields.equals(other.fields);
    }

    private static String requireText(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw validation(fieldName, "字段不能为空");
        }
        return value.trim();
    }

    private static MaintenanceValidationException validation(String fieldName, String message) {
        return new MaintenanceValidationException("MaintenanceFieldCatalogSnapshot", fieldName, message);
    }
}
