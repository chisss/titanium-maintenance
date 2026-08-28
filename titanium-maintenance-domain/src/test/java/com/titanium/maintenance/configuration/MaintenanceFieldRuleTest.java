package com.titanium.maintenance.configuration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.enums.config.MaintenanceFieldValidationType;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.valueobject.change.MaintenanceFieldValue;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldValueType;

class MaintenanceFieldRuleTest {

    @Test
    void shouldDefaultHistoricalRuleToNoFormatValidation() {
        MaintenanceFieldRule rule = new MaintenanceFieldRule(
                "policy.contact.email", false, true, true, true, null,
                PolicyFieldValueType.TEXT, null, null, null);

        assertEquals(MaintenanceFieldValidationType.NONE, rule.validationType());
        assertDoesNotThrow(() -> rule.validateValue(MaintenanceFieldValue.text("not-an-email")));
    }

    @Test
    void shouldValidatePresetEmailFormat() {
        MaintenanceFieldRule rule = rule(MaintenanceFieldValidationType.EMAIL, null, "请输入有效邮箱");

        assertDoesNotThrow(() -> rule.validateValue(MaintenanceFieldValue.text("operator@example.com")));
        MaintenanceValidationException exception = assertThrows(
                MaintenanceValidationException.class,
                () -> rule.validateValue(MaintenanceFieldValue.text("operator@invalid")));
        assertEquals("命令 MaintenanceFieldRule 字段 policy.contact.email 校验失败: 请输入有效邮箱",
                exception.getMessage());
    }

    @Test
    void shouldRequireValidPatternForCustomRegex() {
        assertThrows(MaintenanceValidationException.class,
                () -> rule(MaintenanceFieldValidationType.CUSTOM_REGEX, null, null));
        assertThrows(MaintenanceValidationException.class,
                () -> rule(MaintenanceFieldValidationType.CUSTOM_REGEX, "[", null));

        MaintenanceFieldRule rule = rule(
                MaintenanceFieldValidationType.CUSTOM_REGEX, "^[A-Z]{2}-\\d{4}$", null);
        assertDoesNotThrow(() -> rule.validateValue(MaintenanceFieldValue.text("AB-2026")));
        assertThrows(MaintenanceValidationException.class,
                () -> rule.validateValue(MaintenanceFieldValue.text("invalid")));
    }

    @Test
    void shouldRejectBacktrackingOnlyRegexSyntax() {
        MaintenanceValidationException exception = assertThrows(
                MaintenanceValidationException.class,
                () -> rule(MaintenanceFieldValidationType.CUSTOM_REGEX, "^([A-Z]+)-\\1$", null));

        assertEquals(
                "命令 MaintenanceFieldRule 字段 validationPattern 校验失败: 自定义正则表达式不合法或包含不支持的高风险语法",
                exception.getMessage());
    }

    @Test
    void shouldRejectOversizedValueBeforeFormatMatching() {
        MaintenanceFieldRule rule = rule(MaintenanceFieldValidationType.CUSTOM_REGEX, "^.*$", null);

        MaintenanceValidationException exception = assertThrows(
                MaintenanceValidationException.class,
                () -> rule.validateValue(MaintenanceFieldValue.text("A".repeat(64 * 1024 + 1))));
        assertEquals(
                "命令 MaintenanceFieldRule 字段 policy.contact.email 校验失败: 待校验字段值长度不能超过 65536",
                exception.getMessage());
    }

    private MaintenanceFieldRule rule(
            MaintenanceFieldValidationType validationType,
            String validationPattern,
            String validationMessage) {
        return new MaintenanceFieldRule(
                "policy.contact.email", false, true, true, true, null,
                PolicyFieldValueType.TEXT, validationType, validationPattern, validationMessage);
    }
}
