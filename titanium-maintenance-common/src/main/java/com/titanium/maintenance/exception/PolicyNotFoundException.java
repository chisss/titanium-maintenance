package com.titanium.maintenance.exception;

import com.titanium.maintenance.constant.MaintenanceConstants;
import org.springframework.http.HttpStatus;

public class PolicyNotFoundException extends BusinessException {
    public PolicyNotFoundException() {
        super(MaintenanceConstants.POLICY_NOT_FOUND, "POLICY_NOT_FOUND", HttpStatus.NOT_FOUND);
    }
}