package com.titanium.maintenance.common.exception;

import org.springframework.http.HttpStatus;

import com.titanium.maintenance.common.constant.MaintenanceConstants;

public class PolicyNotFoundException extends BusinessException {
    public PolicyNotFoundException() {
        super(MaintenanceConstants.POLICY_NOT_FOUND, "POLICY_NOT_FOUND", HttpStatus.NOT_FOUND);
    }
}
