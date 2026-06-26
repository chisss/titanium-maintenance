package com.titanium.maintenance.exception;

import com.titanium.maintenance.constant.MaintenanceConstants;

public class MaintenanceTypeExcludedException extends BusinessException {
    public MaintenanceTypeExcludedException() {
        super(MaintenanceConstants.MAINTENANCE_TYPE_EXCLUDED);
    }

    public MaintenanceTypeExcludedException(String message) {
        super(message);
    }
}