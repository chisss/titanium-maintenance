package com.titanium.maintenance.exception;

import org.springframework.http.HttpStatus;

import com.titanium.maintenance.common.exception.BusinessException;

/** 保全项配置唯一键、有效期或乐观锁冲突。 */
public class MaintenanceConfigurationConflictException extends BusinessException {

    public MaintenanceConfigurationConflictException(String message) {
        super(message, "MAINTENANCE_CONFIGURATION_CONFLICT", HttpStatus.CONFLICT);
    }
}
