package com.titanium.maintenance.common.exception;

import org.springframework.http.HttpStatus;

import com.titanium.maintenance.common.constant.MaintenanceConstants;

public class InvalidMaintenanceTypeException extends BusinessException {
    public InvalidMaintenanceTypeException() {
        super(MaintenanceConstants.INVALID_MAINTENANCE_TYPE, "INVALID_MAINTENANCE_TYPE", HttpStatus.BAD_REQUEST);
    }
}
