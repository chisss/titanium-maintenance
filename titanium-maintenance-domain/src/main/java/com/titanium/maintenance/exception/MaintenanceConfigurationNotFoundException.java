package com.titanium.maintenance.exception;

import org.springframework.http.HttpStatus;

import com.titanium.maintenance.common.exception.BusinessException;

/** 租户范围内未找到保全项配置。 */
public class MaintenanceConfigurationNotFoundException extends BusinessException {

    public MaintenanceConfigurationNotFoundException() {
        super("保全项配置不存在", "MAINTENANCE_CONFIGURATION_NOT_FOUND", HttpStatus.NOT_FOUND);
    }
}
