package com.titanium.maintenance.exception;

import com.titanium.maintenance.constant.MaintenanceConstants;
import org.springframework.http.HttpStatus;

public class InvalidMaintenanceStatusException extends BusinessException {
    public InvalidMaintenanceStatusException() {
        super(MaintenanceConstants.INVALID_MAINTENANCE_STATUS, "INVALID_MAINTENANCE_STATUS", HttpStatus.BAD_REQUEST);
    }
}