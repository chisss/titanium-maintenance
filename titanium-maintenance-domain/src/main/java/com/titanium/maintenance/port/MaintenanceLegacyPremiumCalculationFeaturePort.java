package com.titanium.maintenance.port;

/** 旧版人工保费计算写入口灰度开关端口。 */
public interface MaintenanceLegacyPremiumCalculationFeaturePort {

    /** 判断指定租户是否允许人工提交保全保费金额。 */
    boolean isEnabled(String tenantId);
}
