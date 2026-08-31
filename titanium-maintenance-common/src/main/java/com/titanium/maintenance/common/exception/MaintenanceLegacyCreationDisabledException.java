package com.titanium.maintenance.common.exception;


import com.titanium.metadata.errorcode.MaintenanceErrorCode;

/** 旧版保全建案入口已通过部署开关关闭。 */
public class MaintenanceLegacyCreationDisabledException extends BusinessException {

    public MaintenanceLegacyCreationDisabledException() {
        super("旧版保全建案入口当前未启用",
                MaintenanceErrorCode.MAINTENANCE_LEGACY_CREATION_DISABLED);
    }
}
