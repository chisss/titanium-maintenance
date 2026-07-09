package com.titanium.maintenance.common.exception;

import org.springframework.http.HttpStatus;

import com.titanium.maintenance.common.constant.MaintenanceConstants;

public class MaintenanceNotFoundException extends BusinessException {
    public MaintenanceNotFoundException() {
        super(MaintenanceConstants.MAINTENANCE_NOT_FOUND, "MAINTENANCE_NOT_FOUND", HttpStatus.NOT_FOUND);
    }
}
