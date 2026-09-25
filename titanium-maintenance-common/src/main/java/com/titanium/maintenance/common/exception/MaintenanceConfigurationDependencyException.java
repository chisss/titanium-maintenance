package com.titanium.maintenance.common.exception;

import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.exception.DomainException;

/** 保全项配置权威引用提供端不可用。 */
public class MaintenanceConfigurationDependencyException extends DomainException {

    public MaintenanceConfigurationDependencyException(String message) {
        super(MaintenanceErrorCode.MAINTENANCE_CONFIGURATION_DEPENDENCY_UNAVAILABLE, message);
    }
}
