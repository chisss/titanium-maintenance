package com.titanium.maintenance.common.exception;

import com.titanium.maintenance.common.constant.MaintenanceConstants;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.exception.DomainException;

public class PolicyNotActiveException extends DomainException {
    public PolicyNotActiveException() {
        super(MaintenanceErrorCode.POLICY_NOT_ACTIVE, MaintenanceConstants.POLICY_NOT_ACTIVE);
    }
}
