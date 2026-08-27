package com.titanium.maintenance.exception;

import org.springframework.http.HttpStatus;

import com.titanium.maintenance.common.exception.BusinessException;

/** 旧版人工保费计算写入口已通过部署开关关闭。 */
public class MaintenanceLegacyPremiumCalculationDisabledException extends BusinessException {

    public MaintenanceLegacyPremiumCalculationDisabledException() {
        super("旧版人工保费计算入口当前未启用，请使用任务级保全收退费流程",
                "MAINTENANCE_LEGACY_PREMIUM_CALCULATION_DISABLED", HttpStatus.SERVICE_UNAVAILABLE);
    }
}
