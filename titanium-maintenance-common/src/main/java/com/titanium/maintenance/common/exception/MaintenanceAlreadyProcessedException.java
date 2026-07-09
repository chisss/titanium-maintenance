package com.titanium.maintenance.common.exception;

import org.springframework.http.HttpStatus;

import com.titanium.maintenance.common.constant.MaintenanceConstants;

public class MaintenanceAlreadyProcessedException extends BusinessException {
    public MaintenanceAlreadyProcessedException() {
        super(MaintenanceConstants.MAINTENANCE_ALREADY_PROCESSED, "MAINTENANCE_ALREADY_PROCESSED", HttpStatus.BAD_REQUEST);
    }
}
