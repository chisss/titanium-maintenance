package com.titanium.maintenance.common.exception;


import com.titanium.maintenance.common.constant.MaintenanceConstants;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;

public class MaintenanceAlreadyProcessedException extends BusinessException {
    public MaintenanceAlreadyProcessedException() {
        super(MaintenanceConstants.MAINTENANCE_ALREADY_PROCESSED, MaintenanceErrorCode.MAINTENANCE_ALREADY_PROCESSED);
    }
}
