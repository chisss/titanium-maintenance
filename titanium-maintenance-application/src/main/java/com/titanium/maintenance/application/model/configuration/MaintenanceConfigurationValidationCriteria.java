package com.titanium.maintenance.application.model.configuration;

import java.time.LocalDate;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** 保全项配置外部权威校验所需的产品与业务时点。 */
public record MaintenanceConfigurationValidationCriteria(
        String productType, String policyType, LocalDate businessDate) {

    public MaintenanceConfigurationValidationCriteria {
        productType = normalize(productType);
        policyType = normalize(policyType);
        if (businessDate == null) {
            throw new MaintenanceValidationException(
                    "MaintenanceConfigurationValidationCriteria", "businessDate", "业务日期不能为空");
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
