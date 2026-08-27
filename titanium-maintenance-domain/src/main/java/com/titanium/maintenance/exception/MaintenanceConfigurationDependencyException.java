package com.titanium.maintenance.exception;

import org.springframework.http.HttpStatus;

import com.titanium.maintenance.common.exception.BusinessException;

/** 保全项配置权威引用提供端不可用。 */
public class MaintenanceConfigurationDependencyException extends BusinessException {

    public MaintenanceConfigurationDependencyException(String message) {
        super(message, "MAINTENANCE_CONFIGURATION_DEPENDENCY_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE);
    }
}
