package com.titanium.maintenance.common.exception;


import com.titanium.maintenance.common.constant.MaintenanceConstants;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;

public class MaintenanceNotFoundException extends BusinessException {
    public MaintenanceNotFoundException() {
        super(MaintenanceConstants.MAINTENANCE_NOT_FOUND, MaintenanceErrorCode.MAINTENANCE_NOT_FOUND);
    }
}
