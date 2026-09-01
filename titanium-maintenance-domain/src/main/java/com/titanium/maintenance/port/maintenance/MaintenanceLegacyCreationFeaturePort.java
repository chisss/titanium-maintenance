package com.titanium.maintenance.port.maintenance;

/** 旧版保全建案入口灰度开关端口。 */
public interface MaintenanceLegacyCreationFeaturePort {

    /** 判断指定租户是否允许通过旧版入口创建保全案件。 */
    boolean isEnabled(String tenantId);
}
