package com.titanium.maintenance.common.exception;

import com.titanium.maintenance.common.constant.MaintenanceConstants;

public class PolicyNotTerminatedException extends BusinessException {
    public PolicyNotTerminatedException() {
        super(MaintenanceConstants.POLICY_NOT_TERMINATED);
    }

    public PolicyNotTerminatedException(String message) {
        super(message);
    }
}
