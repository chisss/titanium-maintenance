package com.titanium.maintenance.common.exception;


import com.titanium.metadata.errorcode.MaintenanceErrorCode;

/** 保全配置写能力已通过灰度开关关闭。 */
public class MaintenanceConfigurationFeatureDisabledException extends BusinessException {

    public MaintenanceConfigurationFeatureDisabledException() {
        super("保全配置写能力当前未启用",
                MaintenanceErrorCode.MAINTENANCE_CONFIGURATION_FEATURE_DISABLED);
    }
}
