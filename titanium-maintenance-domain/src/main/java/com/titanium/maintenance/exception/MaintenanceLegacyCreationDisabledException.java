package com.titanium.maintenance.exception;

import org.springframework.http.HttpStatus;

import com.titanium.maintenance.common.exception.BusinessException;

/** 旧版保全建案入口已通过部署开关关闭。 */
public class MaintenanceLegacyCreationDisabledException extends BusinessException {

    public MaintenanceLegacyCreationDisabledException() {
        super("旧版保全建案入口当前未启用",
                "MAINTENANCE_LEGACY_CREATION_DISABLED", HttpStatus.SERVICE_UNAVAILABLE);
    }
}
