package com.titanium.maintenance.exception;

import org.springframework.http.HttpStatus;

import com.titanium.maintenance.common.exception.BusinessException;

/** 保全项配置完整发布校验失败。 */
public class MaintenanceConfigurationValidationException extends BusinessException {

    public MaintenanceConfigurationValidationException(String message) {
        super(message, "MAINTENANCE_CONFIGURATION_VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
