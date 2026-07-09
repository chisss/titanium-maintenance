package com.titanium.maintenance.common.exception;

import com.titanium.maintenance.common.constant.MaintenanceConstants;

public class PendingMaintenanceExistsException extends BusinessException {
    public PendingMaintenanceExistsException() {
        super(MaintenanceConstants.PENDING_MAINTENANCE_EXISTS);
    }

    public PendingMaintenanceExistsException(String message) {
        super(message);
    }
}
