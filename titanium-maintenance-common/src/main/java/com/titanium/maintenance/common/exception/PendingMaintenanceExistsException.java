package com.titanium.maintenance.common.exception;

import com.titanium.maintenance.common.constant.MaintenanceConstants;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;

public class PendingMaintenanceExistsException extends BusinessException {
    public PendingMaintenanceExistsException() {
        super(MaintenanceConstants.PENDING_MAINTENANCE_EXISTS, MaintenanceErrorCode.PENDING_MAINTENANCE_EXISTS);
    }

    public PendingMaintenanceExistsException(String message) {
        super(message, MaintenanceErrorCode.PENDING_MAINTENANCE_EXISTS);
    }
}
