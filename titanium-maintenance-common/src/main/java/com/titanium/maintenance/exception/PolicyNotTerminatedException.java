package com.titanium.maintenance.exception;

import com.titanium.maintenance.constant.MaintenanceConstants;

public class PolicyNotTerminatedException extends BusinessException {
    public PolicyNotTerminatedException() {
        super(MaintenanceConstants.POLICY_NOT_TERMINATED);
    }

    public PolicyNotTerminatedException(String message) {
        super(message);
    }
}
