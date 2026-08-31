package com.titanium.maintenance.common.exception;

import com.titanium.maintenance.common.constant.MaintenanceConstants;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;

public class MaintenanceTypeExcludedException extends BusinessException {
    public MaintenanceTypeExcludedException() {
        super(MaintenanceConstants.MAINTENANCE_TYPE_EXCLUDED, MaintenanceErrorCode.MAINTENANCE_TYPE_EXCLUDED);
    }

    public MaintenanceTypeExcludedException(String message) {
        super(message, MaintenanceErrorCode.MAINTENANCE_TYPE_EXCLUDED);
    }
}
