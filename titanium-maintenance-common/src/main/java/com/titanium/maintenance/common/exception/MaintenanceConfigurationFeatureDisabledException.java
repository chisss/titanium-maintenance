package com.titanium.maintenance.common.exception;

import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.exception.DomainException;

/** 保全配置写能力已通过灰度开关关闭。 */
public class MaintenanceConfigurationFeatureDisabledException extends DomainException {

    public MaintenanceConfigurationFeatureDisabledException() {
        super(MaintenanceErrorCode.MAINTENANCE_CONFIGURATION_FEATURE_DISABLED, "保全配置写能力当前未启用");
    }
}
