package com.titanium.maintenance.common.exception;

import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.exception.DomainException;

/** 旧版保全建案入口已通过部署开关关闭。 */
public class MaintenanceLegacyCreationDisabledException extends DomainException {

    public MaintenanceLegacyCreationDisabledException() {
        super(MaintenanceErrorCode.MAINTENANCE_LEGACY_CREATION_DISABLED, "旧版保全建案入口当前未启用");
    }
}
