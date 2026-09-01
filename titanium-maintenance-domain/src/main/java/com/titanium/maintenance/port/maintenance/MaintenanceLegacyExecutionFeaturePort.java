package com.titanium.maintenance.port.maintenance;

/** 旧版整案执行写入口灰度开关端口。 */
public interface MaintenanceLegacyExecutionFeaturePort {

    /** 判断指定租户是否允许继续通过旧入口产生执行事件。 */
    boolean isEnabled(String tenantId);
}
