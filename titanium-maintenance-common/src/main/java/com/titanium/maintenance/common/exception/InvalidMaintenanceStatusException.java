package com.titanium.maintenance.common.exception;


import com.titanium.maintenance.common.constant.MaintenanceConstants;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;

public class InvalidMaintenanceStatusException extends BusinessException {
    public InvalidMaintenanceStatusException() {
        super(MaintenanceConstants.INVALID_MAINTENANCE_STATUS, MaintenanceErrorCode.MAINTENANCE_STATUS_INVALID);
    }
}
