package com.titanium.maintenance.common.exception;

import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.exception.DomainException;

/** Policy 字段目录不可用或远程契约不可信。 */
public class PolicyFieldCatalogUnavailableException extends DomainException {

    private static final MaintenanceErrorCode ERROR_CODE = MaintenanceErrorCode.MAINTENANCE_POLICY_FIELD_CATALOG_UNAVAILABLE;

    public PolicyFieldCatalogUnavailableException(String message) {
        super(ERROR_CODE, message);
    }

    public PolicyFieldCatalogUnavailableException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}
