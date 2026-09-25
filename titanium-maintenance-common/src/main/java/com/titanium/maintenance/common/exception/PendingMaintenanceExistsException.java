package com.titanium.maintenance.common.exception;

import com.titanium.maintenance.common.constant.MaintenanceConstants;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.exception.DomainException;

public class PendingMaintenanceExistsException extends DomainException {
    public PendingMaintenanceExistsException() {
        super(MaintenanceErrorCode.PENDING_MAINTENANCE_EXISTS, MaintenanceConstants.PENDING_MAINTENANCE_EXISTS);
    }

    public PendingMaintenanceExistsException(String message) {
        super(MaintenanceErrorCode.PENDING_MAINTENANCE_EXISTS, message);
    }
}
