package com.titanium.maintenance.common.exception;

import com.titanium.maintenance.common.constant.MaintenanceConstants;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.exception.DomainException;

public class InvalidMaintenanceTypeException extends DomainException {
    public InvalidMaintenanceTypeException() {
        super(MaintenanceErrorCode.MAINTENANCE_TYPE_INVALID, MaintenanceConstants.INVALID_MAINTENANCE_TYPE);
    }
}
