package com.titanium.maintenance.common.exception;

import com.titanium.maintenance.common.constant.MaintenanceConstants;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.exception.DomainException;

public class MaintenanceTypeExcludedException extends DomainException {
    public MaintenanceTypeExcludedException() {
        super(MaintenanceErrorCode.MAINTENANCE_TYPE_EXCLUDED, MaintenanceConstants.MAINTENANCE_TYPE_EXCLUDED);
    }

    public MaintenanceTypeExcludedException(String message) {
        super(MaintenanceErrorCode.MAINTENANCE_TYPE_EXCLUDED, message);
    }
}
