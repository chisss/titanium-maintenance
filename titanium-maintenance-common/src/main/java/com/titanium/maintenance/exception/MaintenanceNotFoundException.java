package com.titanium.maintenance.exception;

import com.titanium.maintenance.constant.MaintenanceConstants;
import org.springframework.http.HttpStatus;

public class MaintenanceNotFoundException extends BusinessException {
    public MaintenanceNotFoundException() {
        super(MaintenanceConstants.MAINTENANCE_NOT_FOUND, "MAINTENANCE_NOT_FOUND", HttpStatus.NOT_FOUND);
    }
}