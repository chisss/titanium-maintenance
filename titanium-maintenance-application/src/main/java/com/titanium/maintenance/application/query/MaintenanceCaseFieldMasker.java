package com.titanium.maintenance.application.query;

import org.springframework.stereotype.Component;

import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldMaskingPolicy;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldSensitivityLevel;

/** 对保全字段查询值执行目录指定的默认掩码；目录证据缺失时失败关闭。 */
@Component
public class MaintenanceCaseFieldMasker {

    public String mask(
            String value,
            PolicyFieldSensitivityLevel sensitivity,
            PolicyFieldMaskingPolicy maskingPolicy,
            boolean sensitiveDetailsVisible) {
        if (value == null || sensitiveDetailsVisible) {
            return value;
        }
        if (sensitivity != null && !sensitivity.requiresMasking()) {
            return value;
        }
        if (maskingPolicy == null || maskingPolicy == PolicyFieldMaskingPolicy.NONE) {
            return "***";
        }
        return switch (maskingPolicy) {
            case NAME -> first(value) + "**";
            case MOBILE -> retainEnds(value, 3, 4);
            case EMAIL -> maskEmail(value);
            case ADDRESS -> retainStart(value, 3);
            case ID_NUMBER, BANK_ACCOUNT -> retainEnd(value, 4);
            case DATE -> "****-**-**";
            case PARTIAL_TEXT -> retainEnds(value, 1, 1);
            case NONE -> "***";
        };
    }

    private String maskEmail(String value) {
        int separator = value.indexOf('@');
        if (separator <= 0 || separator == value.length() - 1) {
            return "***";
        }
        return first(value.substring(0, separator)) + "***" + value.substring(separator);
    }

    private String retainStart(String value, int count) {
        return value.length() <= count ? "***" : value.substring(0, count) + "***";
    }

    private String retainEnd(String value, int count) {
        return value.length() <= count ? "***" : "***" + value.substring(value.length() - count);
    }

    private String retainEnds(String value, int prefix, int suffix) {
        if (value.length() <= prefix + suffix) {
            return "***";
        }
        return value.substring(0, prefix) + "****" + value.substring(value.length() - suffix);
    }

    private String first(String value) {
        return value.isEmpty() ? "" : value.substring(0, 1);
    }
}
