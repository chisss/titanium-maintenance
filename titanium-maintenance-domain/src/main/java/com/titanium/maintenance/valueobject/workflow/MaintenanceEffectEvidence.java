package com.titanium.maintenance.valueobject.workflow;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.titanium.maintenance.common.exception.MaintenanceValidationException;

/** 生效请求及其可选 Policy 成功回执。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MaintenanceEffectEvidence(
        MaintenanceEffectRequestEvidence request,
        MaintenancePolicyApplicationEvidence application) {

    public MaintenanceEffectEvidence {
        if (request == null) {
            throw new MaintenanceValidationException(
                    "MaintenanceEffectEvidence", "request", "生效请求证据不能为空");
        }
        if (application != null
                && (!request.requestId().equals(application.requestId())
                        || request.expectedPolicyVersion() != application.expectedPolicyVersion())) {
            throw new MaintenanceValidationException(
                    "MaintenanceEffectEvidence", "application", "Policy 回执与生效请求不一致");
        }
    }

    public static MaintenanceEffectEvidence requested(MaintenanceEffectRequestEvidence request) {
        return new MaintenanceEffectEvidence(request, null);
    }

    public MaintenanceEffectEvidence applied(MaintenancePolicyApplicationEvidence application) {
        return new MaintenanceEffectEvidence(request, application);
    }

    @JsonIgnore
    public boolean isApplied() {
        return application != null;
    }
}
