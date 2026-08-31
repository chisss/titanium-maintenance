package com.titanium.maintenance.common.exception;


import com.titanium.metadata.errorcode.MaintenanceErrorCode;

/** 保全项配置权威引用提供端不可用。 */
public class MaintenanceConfigurationDependencyException extends BusinessException {

    public MaintenanceConfigurationDependencyException(String message) {
        super(message, MaintenanceErrorCode.MAINTENANCE_CONFIGURATION_DEPENDENCY_UNAVAILABLE);
    }
}
