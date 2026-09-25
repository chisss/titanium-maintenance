package com.titanium.maintenance.common.exception;

import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.exception.DomainException;

/** 保全项配置唯一键、有效期或乐观锁冲突。 */
public class MaintenanceConfigurationConflictException extends DomainException {

    public MaintenanceConfigurationConflictException(String message) {
        super(MaintenanceErrorCode.MAINTENANCE_CONFIGURATION_CONFLICT, message);
    }
}
