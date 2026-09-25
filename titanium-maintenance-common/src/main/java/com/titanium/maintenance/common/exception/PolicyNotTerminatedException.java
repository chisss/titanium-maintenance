package com.titanium.maintenance.common.exception;

import com.titanium.maintenance.common.constant.MaintenanceConstants;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.exception.DomainException;

public class PolicyNotTerminatedException extends DomainException {
    public PolicyNotTerminatedException() {
        super(MaintenanceErrorCode.POLICY_NOT_TERMINATED, MaintenanceConstants.POLICY_NOT_TERMINATED);
    }

    public PolicyNotTerminatedException(String message) {
        super(MaintenanceErrorCode.POLICY_NOT_TERMINATED, message);
    }
}
