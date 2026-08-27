package com.titanium.maintenance.configuration.control;

import java.util.Set;
import java.util.stream.Collectors;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** 保全凭证、通知与归档模板引用。 */
public record MaintenanceOutputRule(String voucherTemplateCode, Set<String> notificationTemplateCodes,
        String archiveTemplateCode) {

    public MaintenanceOutputRule {
        voucherTemplateCode = normalize(voucherTemplateCode);
        archiveTemplateCode = normalize(archiveTemplateCode);
        notificationTemplateCodes = immutableTextSet(notificationTemplateCodes);
    }

    /** 创建无输出模板的草稿规则。 */
    public static MaintenanceOutputRule empty() {
        return new MaintenanceOutputRule(null, Set.of(), null);
    }

    private static Set<String> immutableTextSet(Set<String> values) {
        if (values == null) {
            return Set.of();
        }
        if (values.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new MaintenanceValidationException(
                    "MaintenanceOutputRule", "notificationTemplateCodes", "通知模板集合不能包含空项");
        }
        return values.stream().map(String::trim).collect(Collectors.toUnmodifiableSet());
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
