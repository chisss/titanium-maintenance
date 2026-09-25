package com.titanium.maintenance.common.exception;

import com.titanium.maintenance.common.constant.MaintenanceConstants;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.exception.DomainException;

public class PolicyNotFoundException extends DomainException {
    public PolicyNotFoundException() {
        super(MaintenanceErrorCode.POLICY_NOT_FOUND, MaintenanceConstants.POLICY_NOT_FOUND);
    }
}
