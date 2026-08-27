package com.titanium.maintenance.infrastructure.adapter;

import com.titanium.maintenance.port.MaintenanceConfigurationReferencePort;

/** 未接入权威引用注册表时使用的失败关闭适配器。 */
public class UnavailableMaintenanceConfigurationReferenceAdapter
        implements MaintenanceConfigurationReferencePort {

    @Override
    public ReferenceValidationEvidence validate(ReferenceValidationRequest request) {
        return ReferenceValidationEvidence.unavailable(
                "规则、权限与模板权威只读校验 API 尚未接入");
    }
}
