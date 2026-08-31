package com.titanium.maintenance.common.exception;

import com.titanium.maintenance.common.constant.MaintenanceConstants;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;

public class PolicyNotTerminatedException extends BusinessException {
    public PolicyNotTerminatedException() {
        super(MaintenanceConstants.POLICY_NOT_TERMINATED, MaintenanceErrorCode.POLICY_NOT_TERMINATED);
    }

    public PolicyNotTerminatedException(String message) {
        super(message, MaintenanceErrorCode.POLICY_NOT_TERMINATED);
    }
}
