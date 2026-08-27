package com.titanium.maintenance.valueobject.item;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.metadata.enums.maintenance.MaintenanceType;

/** 保全项稳定编码及旧案件主类型映射。 */
public record MaintenanceItemCode(String value) {

    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{2,63}");
    private static final Map<String, MaintenanceType> LEGACY_TYPE_BY_ITEM_CODE = legacyTypeMappings();

    public MaintenanceItemCode {
        if (value == null || !CODE_PATTERN.matcher(value.trim()).matches()) {
            throw new MaintenanceValidationException(
                    "MaintenanceItemCode", "value", "保全项编码必须使用大写字母、数字和下划线");
        }
        value = value.trim();
    }

    public static MaintenanceItemCode of(String value) {
        return new MaintenanceItemCode(value);
    }

    /** 将首个保全项映射到兼容旧事件和下游契约的主保全类型。 */
    public MaintenanceType legacyMaintenanceType() {
        MaintenanceType type = LEGACY_TYPE_BY_ITEM_CODE.get(value);
        if (type == null) {
            throw new MaintenanceValidationException(
                    "MaintenanceItemCode", "value", "保全项缺少旧主类型映射: " + value);
        }
        return type;
    }

    private static Map<String, MaintenanceType> legacyTypeMappings() {
        Map<String, MaintenanceType> mappings = new HashMap<>();
        for (MaintenanceType type : MaintenanceType.values()) {
            mappings.put(type.getCode(), type);
        }
        mappings.put("SURRENDER", MaintenanceType.POLICY_TERMINATION);
        mappings.put("POLICY_SURRENDER", MaintenanceType.POLICY_TERMINATION);
        return Map.copyOf(mappings);
    }
}
