package com.titanium.maintenance.valueobject.change;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONWriter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.titanium.maintenance.common.enums.change.PolicyFieldDataType;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/**
 * 可稳定序列化的强类型字段值。
 *
 * <p>事件中保存规范化文本与明确类型，避免反序列化为不确定的 {@code Object} 类型。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MaintenanceFieldValue(PolicyFieldDataType dataType, String canonicalValue) {

    public MaintenanceFieldValue {
        if (dataType == null) {
            throw new MaintenanceValidationException("MaintenanceFieldValue", "dataType", "字段类型不能为空");
        }
        canonicalValue = normalize(dataType, canonicalValue);
    }

    public static MaintenanceFieldValue nullValue(PolicyFieldDataType dataType) {
        return new MaintenanceFieldValue(dataType, null);
    }

    public static MaintenanceFieldValue text(String value) {
        return new MaintenanceFieldValue(PolicyFieldDataType.TEXT, value);
    }

    public static MaintenanceFieldValue integer(long value) {
        return new MaintenanceFieldValue(PolicyFieldDataType.INTEGER, Long.toString(value));
    }

    public static MaintenanceFieldValue decimal(BigDecimal value) {
        return new MaintenanceFieldValue(PolicyFieldDataType.DECIMAL, value == null ? null : value.toPlainString());
    }

    public static MaintenanceFieldValue bool(boolean value) {
        return new MaintenanceFieldValue(PolicyFieldDataType.BOOLEAN, Boolean.toString(value));
    }

    public static MaintenanceFieldValue date(LocalDate value) {
        return new MaintenanceFieldValue(PolicyFieldDataType.DATE, value == null ? null : value.toString());
    }

    public static MaintenanceFieldValue dateTime(OffsetDateTime value) {
        return new MaintenanceFieldValue(PolicyFieldDataType.DATETIME, value == null ? null : value.toString());
    }

    public static MaintenanceFieldValue enumValue(String value) {
        return new MaintenanceFieldValue(PolicyFieldDataType.ENUM, value);
    }

    public static MaintenanceFieldValue object(String json) {
        return new MaintenanceFieldValue(PolicyFieldDataType.OBJECT, json);
    }

    public static MaintenanceFieldValue array(String json) {
        return new MaintenanceFieldValue(PolicyFieldDataType.ARRAY, json);
    }

    @JsonIgnore
    public boolean isNull() {
        return canonicalValue == null;
    }

    private static String normalize(PolicyFieldDataType dataType, String value) {
        if (value == null) {
            return null;
        }
        try {
            return switch (dataType) {
                case TEXT -> value;
                case ENUM -> normalizeEnum(value);
                case INTEGER -> new BigInteger(value).toString();
                case DECIMAL -> normalizeDecimal(value);
                case BOOLEAN -> normalizeBoolean(value);
                case DATE -> LocalDate.parse(value).toString();
                case DATETIME -> OffsetDateTime.parse(value).toString();
                case OBJECT -> JSON.toJSONString(JSON.parseObject(value), JSONWriter.Feature.MapSortField);
                case ARRAY -> JSON.toJSONString(JSON.parseArray(value), JSONWriter.Feature.MapSortField);
            };
        } catch (NumberFormatException | DateTimeException | JSONException exception) {
            throw invalidValue(dataType, exception);
        }
    }

    private static String normalizeEnum(String value) {
        if (value.isBlank()) {
            throw new MaintenanceValidationException("MaintenanceFieldValue", "canonicalValue", "枚举值不能为空");
        }
        return value.trim();
    }

    private static String normalizeDecimal(String value) {
        BigDecimal decimal = new BigDecimal(value).stripTrailingZeros();
        return decimal.signum() == 0 ? "0" : decimal.toPlainString();
    }

    private static String normalizeBoolean(String value) {
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            throw new MaintenanceValidationException("MaintenanceFieldValue", "canonicalValue", "布尔值只能为 true 或 false");
        }
        return value.toLowerCase();
    }

    private static MaintenanceValidationException invalidValue(PolicyFieldDataType dataType, Exception exception) {
        return new MaintenanceValidationException(
                "MaintenanceFieldValue", "canonicalValue", "字段值不符合 " + dataType.getCode() + " 类型: " + exception.getMessage());
    }
}
