package com.titanium.maintenance.exception;

import org.springframework.http.HttpStatus;

import com.titanium.maintenance.common.exception.BusinessException;

/** 保全配置写能力已通过灰度开关关闭。 */
public class MaintenanceConfigurationFeatureDisabledException extends BusinessException {

    public MaintenanceConfigurationFeatureDisabledException() {
        super("保全配置写能力当前未启用",
                "MAINTENANCE_CONFIGURATION_FEATURE_DISABLED", HttpStatus.SERVICE_UNAVAILABLE);
    }
}
