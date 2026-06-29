package com.titanium.maintenance.exception;

import org.springframework.http.HttpStatus;

import com.titanium.maintenance.constant.MaintenanceConstants;

public class PolicyNotActiveException extends BusinessException {
    public PolicyNotActiveException() {
        super(MaintenanceConstants.POLICY_NOT_ACTIVE, "POLICY_NOT_ACTIVE", HttpStatus.BAD_REQUEST);
    }
}
