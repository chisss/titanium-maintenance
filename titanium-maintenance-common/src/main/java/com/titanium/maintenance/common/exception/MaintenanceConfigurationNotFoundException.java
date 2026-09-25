package com.titanium.maintenance.common.exception;

import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.exception.DomainException;

/** 租户范围内未找到保全项配置。 */
public class MaintenanceConfigurationNotFoundException extends DomainException {

    public MaintenanceConfigurationNotFoundException() {
        super(MaintenanceErrorCode.MAINTENANCE_CONFIGURATION_NOT_FOUND, "保全项配置不存在");
    }
}
