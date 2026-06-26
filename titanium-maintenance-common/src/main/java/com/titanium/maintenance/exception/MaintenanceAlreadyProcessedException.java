package com.titanium.maintenance.exception;

import com.titanium.maintenance.constant.MaintenanceConstants;
import org.springframework.http.HttpStatus;

public class MaintenanceAlreadyProcessedException extends BusinessException {
    public MaintenanceAlreadyProcessedException() {
        super(MaintenanceConstants.MAINTENANCE_ALREADY_PROCESSED, "MAINTENANCE_ALREADY_PROCESSED", HttpStatus.BAD_REQUEST);
    }
}