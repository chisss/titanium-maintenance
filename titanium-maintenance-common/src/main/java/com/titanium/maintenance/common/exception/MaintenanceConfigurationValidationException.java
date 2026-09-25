package com.titanium.maintenance.common.exception;

import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.exception.DomainException;

/** 保全项配置完整发布校验失败。 */
public class MaintenanceConfigurationValidationException extends DomainException {

    public MaintenanceConfigurationValidationException(String message) {
        super(MaintenanceErrorCode.MAINTENANCE_CONFIGURATION_VALIDATION_FAILED, message);
    }
}
