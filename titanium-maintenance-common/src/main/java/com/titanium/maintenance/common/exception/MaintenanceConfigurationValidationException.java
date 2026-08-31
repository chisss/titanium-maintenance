package com.titanium.maintenance.common.exception;


import com.titanium.metadata.errorcode.MaintenanceErrorCode;

/** 保全项配置完整发布校验失败。 */
public class MaintenanceConfigurationValidationException extends BusinessException {

    public MaintenanceConfigurationValidationException(String message) {
        super(message, MaintenanceErrorCode.MAINTENANCE_CONFIGURATION_VALIDATION_FAILED);
    }
}
