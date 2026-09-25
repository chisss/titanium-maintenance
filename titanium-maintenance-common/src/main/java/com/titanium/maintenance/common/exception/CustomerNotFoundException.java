package com.titanium.maintenance.common.exception;

import com.titanium.maintenance.common.constant.MaintenanceConstants;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.exception.DomainException;

public class CustomerNotFoundException extends DomainException {
    public CustomerNotFoundException() {
        super(MaintenanceErrorCode.CUSTOMER_NOT_FOUND, MaintenanceConstants.CUSTOMER_NOT_FOUND);
    }
}
