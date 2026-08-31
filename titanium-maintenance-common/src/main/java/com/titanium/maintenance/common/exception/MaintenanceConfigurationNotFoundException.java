package com.titanium.maintenance.common.exception;


import com.titanium.metadata.errorcode.MaintenanceErrorCode;

/** 租户范围内未找到保全项配置。 */
public class MaintenanceConfigurationNotFoundException extends BusinessException {

    public MaintenanceConfigurationNotFoundException() {
        super("保全项配置不存在", MaintenanceErrorCode.MAINTENANCE_CONFIGURATION_NOT_FOUND);
    }
}
