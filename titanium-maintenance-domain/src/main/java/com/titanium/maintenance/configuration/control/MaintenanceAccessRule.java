package com.titanium.maintenance.configuration.control;

import java.util.Set;
import java.util.stream.Collectors;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** 保全项操作与查看权限。 */
public record MaintenanceAccessRule(Set<String> operationPermissionCodes, Set<String> viewPermissionCodes) {

    public MaintenanceAccessRule {
        operationPermissionCodes = immutableTextSet("operationPermissionCodes", operationPermissionCodes);
        viewPermissionCodes = immutableTextSet("viewPermissionCodes", viewPermissionCodes);
    }

    /** 创建尚未配置权限的草稿规则。 */
    public static MaintenanceAccessRule empty() {
        return new MaintenanceAccessRule(Set.of(), Set.of());
    }

    /** 校验送审所需权限是否完整。 */
    public void validateForSubmission() {
        if (operationPermissionCodes.isEmpty() || viewPermissionCodes.isEmpty()) {
            throw new MaintenanceValidationException(
                    "MaintenanceAccessRule", "送审前必须配置操作权限码和查看权限码");
        }
    }

    private static Set<String> immutableTextSet(String fieldName, Set<String> values) {
        if (values == null) {
            return Set.of();
        }
        if (values.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new MaintenanceValidationException(
                    "MaintenanceAccessRule", fieldName, "权限码集合不能包含空项");
        }
        return values.stream().map(String::trim).collect(Collectors.toUnmodifiableSet());
    }
}
