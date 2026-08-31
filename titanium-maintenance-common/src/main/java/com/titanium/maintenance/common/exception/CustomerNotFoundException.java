package com.titanium.maintenance.common.exception;


import com.titanium.maintenance.common.constant.MaintenanceConstants;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;

public class CustomerNotFoundException extends BusinessException {
    public CustomerNotFoundException() {
        super(MaintenanceConstants.CUSTOMER_NOT_FOUND, MaintenanceErrorCode.CUSTOMER_NOT_FOUND);
    }
}
