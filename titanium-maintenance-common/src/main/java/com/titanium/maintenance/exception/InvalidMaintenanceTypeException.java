package com.titanium.maintenance.exception;

import com.titanium.maintenance.constant.MaintenanceConstants;
import org.springframework.http.HttpStatus;

public class InvalidMaintenanceTypeException extends BusinessException {
    public InvalidMaintenanceTypeException() {
        super(MaintenanceConstants.INVALID_MAINTENANCE_TYPE, "INVALID_MAINTENANCE_TYPE", HttpStatus.BAD_REQUEST);
    }
}