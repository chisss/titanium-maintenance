package com.titanium.maintenance.common.exception;


import com.titanium.maintenance.common.constant.MaintenanceConstants;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;

public class PolicyNotActiveException extends BusinessException {
    public PolicyNotActiveException() {
        super(MaintenanceConstants.POLICY_NOT_ACTIVE, MaintenanceErrorCode.POLICY_NOT_ACTIVE);
    }
}
