package com.titanium.maintenance.exception;

import com.titanium.maintenance.constant.MaintenanceConstants;

public class PendingMaintenanceExistsException extends BusinessException {
    public PendingMaintenanceExistsException() {
        super(MaintenanceConstants.PENDING_MAINTENANCE_EXISTS);
    }

    public PendingMaintenanceExistsException(String message) {
        super(message);
    }
}
