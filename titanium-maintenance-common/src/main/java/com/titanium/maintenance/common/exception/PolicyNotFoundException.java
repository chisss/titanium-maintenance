package com.titanium.maintenance.common.exception;


import com.titanium.maintenance.common.constant.MaintenanceConstants;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;

public class PolicyNotFoundException extends BusinessException {
    public PolicyNotFoundException() {
        super(MaintenanceConstants.POLICY_NOT_FOUND, MaintenanceErrorCode.POLICY_NOT_FOUND);
    }
}
