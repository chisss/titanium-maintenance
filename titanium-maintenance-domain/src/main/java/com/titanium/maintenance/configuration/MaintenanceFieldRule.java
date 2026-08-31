package com.titanium.maintenance.configuration;

import com.google.re2j.Pattern;
import com.google.re2j.PatternSyntaxException;

import com.titanium.maintenance.common.enums.config.MaintenanceFieldValidationType;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldValue;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldValueType;

/** 保全项可见与可编辑字段规则。 */
public record MaintenanceFieldRule(String fieldCode, boolean required, boolean visible, boolean editable,
        boolean allowClear, String conditionRuleCode, PolicyFieldValueType expectedValueType,
        MaintenanceFieldValidationType validationType, String validationPattern, String validationMessage) {

    private static final int MAX_PATTERN_LENGTH = 256;
    private static final int MAX_VALIDATION_VALUE_LENGTH = 64 * 1024;
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$");
    private static final Pattern MOBILE_CN_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern GENDER_PATTERN = Pattern.compile("^(M|F|UNKNOWN)$");
    private static final Pattern ID_CARD_CN_PATTERN = Pattern.compile("^(?:\\d{15}|\\d{17}[0-9Xx])$");
    private static final Pattern POSTAL_CODE_CN_PATTERN = Pattern.compile("^[1-9]\\d{5}$");

    public MaintenanceFieldRule {
        if (!hasText(fieldCode)) {
            throw new MaintenanceValidationException("MaintenanceFieldRule", "fieldCode", "字段编码不能为空");
        }
        if (editable && !visible) {
            throw new MaintenanceValidationException("MaintenanceFieldRule", "editable", "可编辑字段必须同时可见");
        }
        if (required && !editable) {
            throw new MaintenanceValidationException("MaintenanceFieldRule", "required", "必填字段必须可编辑");
        }
        conditionRuleCode = normalize(conditionRuleCode);
        validationType = validationType == null ? MaintenanceFieldValidationType.NONE : validationType;
        validationPattern = normalize(validationPattern);
        validationMessage = normalize(validationMessage);
        validateFormatConfiguration(validationType, validationPattern);
    }

    /** 兼容 Phase 1 未声明字段类型的辅助构造器。 */
    public MaintenanceFieldRule(String fieldCode, boolean required, boolean visible, boolean editable,
            boolean allowClear, String conditionRuleCode) {
        this(fieldCode, required, visible, editable, allowClear, conditionRuleCode, null,
                MaintenanceFieldValidationType.NONE, null, null);
    }

    /** 兼容未声明格式规则的配置构造器。 */
    public MaintenanceFieldRule(String fieldCode, boolean required, boolean visible, boolean editable,
            boolean allowClear, String conditionRuleCode, PolicyFieldValueType expectedValueType) {
        this(fieldCode, required, visible, editable, allowClear, conditionRuleCode, expectedValueType,
                MaintenanceFieldValidationType.NONE, null, null);
    }

    /** 创建常用的可编辑字段规则。 */
    public static MaintenanceFieldRule editable(String fieldCode, boolean required, boolean allowClear) {
        return new MaintenanceFieldRule(fieldCode, required, true, true, allowClear, null);
    }

    /** 创建声明 Policy 字段值类型的可编辑规则。 */
    public static MaintenanceFieldRule editable(String fieldCode, boolean required, boolean allowClear,
            PolicyFieldValueType expectedValueType) {
        return new MaintenanceFieldRule(
                fieldCode, required, true, true, allowClear, null, expectedValueType);
    }

    /** 校验字段提案值是否符合配置的格式规则。 */
    public void validateValue(MaintenanceFieldValue value) {
        if (value == null || value.isNull() || validationType == MaintenanceFieldValidationType.NONE) {
            return;
        }
        String canonicalValue = value.canonicalValue();
        if (canonicalValue.length() > MAX_VALIDATION_VALUE_LENGTH) {
            throw new MaintenanceValidationException(
                    "MaintenanceFieldRule", fieldCode, "待校验字段值长度不能超过 65536");
        }
        Pattern pattern = switch (validationType) {
            case NONE -> null;
            case EMAIL -> EMAIL_PATTERN;
            case MOBILE_CN -> MOBILE_CN_PATTERN;
            case GENDER -> GENDER_PATTERN;
            case ID_CARD_CN -> ID_CARD_CN_PATTERN;
            case POSTAL_CODE_CN -> POSTAL_CODE_CN_PATTERN;
            case CUSTOM_REGEX -> Pattern.compile(validationPattern);
        };
        if (pattern != null && !pattern.matcher(canonicalValue).matches()) {
            String message = validationMessage == null
                    ? "字段值不符合 " + validationType + " 格式"
                    : validationMessage;
            throw new MaintenanceValidationException("MaintenanceFieldRule", fieldCode, message);
        }
    }

    private static void validateFormatConfiguration(
            MaintenanceFieldValidationType validationType, String validationPattern) {
        if (validationType != MaintenanceFieldValidationType.CUSTOM_REGEX) {
            if (validationPattern != null) {
                throw new MaintenanceValidationException(
                        "MaintenanceFieldRule", "validationPattern", "仅自定义正则校验可以配置表达式");
            }
            return;
        }
        if (validationPattern == null) {
            throw new MaintenanceValidationException(
                    "MaintenanceFieldRule", "validationPattern", "自定义正则校验必须配置表达式");
        }
        if (validationPattern.length() > MAX_PATTERN_LENGTH) {
            throw new MaintenanceValidationException(
                    "MaintenanceFieldRule", "validationPattern", "自定义正则表达式长度不能超过 256");
        }
        try {
            Pattern.compile(validationPattern);
        } catch (PatternSyntaxException exception) {
            throw new MaintenanceValidationException(
                    "MaintenanceFieldRule", "validationPattern", "自定义正则表达式不合法或包含不支持的高风险语法");
        }
    }

    private static String normalize(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
