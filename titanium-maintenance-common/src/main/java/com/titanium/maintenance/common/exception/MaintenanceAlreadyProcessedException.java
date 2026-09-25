package com.titanium.maintenance.common.exception;

import com.titanium.maintenance.common.constant.MaintenanceConstants;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.exception.DomainException;

public class MaintenanceAlreadyProcessedException extends DomainException {
    public MaintenanceAlreadyProcessedException() {
        super(MaintenanceErrorCode.MAINTENANCE_ALREADY_PROCESSED, MaintenanceConstants.MAINTENANCE_ALREADY_PROCESSED);
    }
}
