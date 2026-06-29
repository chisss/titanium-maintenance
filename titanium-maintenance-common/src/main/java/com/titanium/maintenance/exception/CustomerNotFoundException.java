package com.titanium.maintenance.exception;

import org.springframework.http.HttpStatus;

import com.titanium.maintenance.constant.MaintenanceConstants;

public class CustomerNotFoundException extends BusinessException {
    public CustomerNotFoundException() {
        super(MaintenanceConstants.CUSTOMER_NOT_FOUND, "CUSTOMER_NOT_FOUND", HttpStatus.NOT_FOUND);
    }
}
