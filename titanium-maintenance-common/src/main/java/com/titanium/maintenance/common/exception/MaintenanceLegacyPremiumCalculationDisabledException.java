package com.titanium.maintenance.common.exception;

import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.exception.DomainException;

/** 旧版人工保费计算写入口已通过部署开关关闭。 */
public class MaintenanceLegacyPremiumCalculationDisabledException extends DomainException {

    public MaintenanceLegacyPremiumCalculationDisabledException() {
        super(MaintenanceErrorCode.MAINTENANCE_LEGACY_PREMIUM_CALCULATION_DISABLED, "旧版人工保费计算入口当前未启用，请使用任务级保全收退费流程");
    }
}
