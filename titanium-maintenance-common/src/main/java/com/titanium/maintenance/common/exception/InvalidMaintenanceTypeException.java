package com.titanium.maintenance.common.exception;


import com.titanium.maintenance.common.constant.MaintenanceConstants;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;

public class InvalidMaintenanceTypeException extends BusinessException {
    public InvalidMaintenanceTypeException() {
        super(MaintenanceConstants.INVALID_MAINTENANCE_TYPE, MaintenanceErrorCode.MAINTENANCE_TYPE_INVALID);
    }
}
