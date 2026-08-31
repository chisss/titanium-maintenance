package com.titanium.maintenance.common.exception;


import com.titanium.metadata.errorcode.MaintenanceErrorCode;

/** 保全项配置唯一键、有效期或乐观锁冲突。 */
public class MaintenanceConfigurationConflictException extends BusinessException {

    public MaintenanceConfigurationConflictException(String message) {
        super(message, MaintenanceErrorCode.MAINTENANCE_CONFIGURATION_CONFLICT);
    }
}
