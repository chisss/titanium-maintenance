package com.titanium.maintenance.common.exception;

import com.titanium.maintenance.common.constant.MaintenanceConstants;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.exception.DomainException;

public class MaintenanceNotFoundException extends DomainException {
    public MaintenanceNotFoundException() {
        super(MaintenanceErrorCode.MAINTENANCE_NOT_FOUND, MaintenanceConstants.MAINTENANCE_NOT_FOUND);
    }
}
