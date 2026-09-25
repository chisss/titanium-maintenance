package com.titanium.maintenance.common.exception;

import com.titanium.maintenance.common.constant.MaintenanceConstants;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.exception.DomainException;

public class InvalidMaintenanceStatusException extends DomainException {
    public InvalidMaintenanceStatusException() {
        super(MaintenanceErrorCode.MAINTENANCE_STATUS_INVALID, MaintenanceConstants.INVALID_MAINTENANCE_STATUS);
    }
}
