package com.titanium.maintenance.infrastructure.adapter;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.titanium.maintenance.port.MaintenanceConfigurationReferencePort;

/**
 * 本地验收环境引用白名单；生产环境仍由权威规则、权限与模板注册表提供证据。
 */
@Component
@Profile("dev")
@ConditionalOnProperty(name = "maintenance.local-reference-registry.enabled", havingValue = "true")
public class LocalMaintenanceConfigurationReferenceAdapter
        implements MaintenanceConfigurationReferencePort {

    private static final String EVIDENCE_VERSION = "local-reference-registry-v1";

    private static final Set<String> RULE_CODES = Set.of(
            "APPROVAL_STANDARD",
            "MAINTENANCE_PREMIUM_FORMULA",
            "MAINTENANCE_SETTLEMENT_GATE");

    private static final Set<String> PERMISSION_CODES = Set.of(
            "maintenance:item:operate",
            "maintenance:item:view",
            "maintenance:sensitive:view");

    private static final Set<String> TEMPLATE_CODES = Set.of(
            "MAINTENANCE_VOUCHER",
            "MAINTENANCE_NOTICE",
            "MAINTENANCE_ARCHIVE");

    @Override
    public ReferenceValidationEvidence validate(ReferenceValidationRequest request) {
        return new ReferenceValidationEvidence(
                true,
                EVIDENCE_VERSION,
                resolved(request.ruleCodes(), RULE_CODES),
                resolved(request.permissionCodes(), PERMISSION_CODES),
                resolved(request.templateCodes(), TEMPLATE_CODES),
                null);
    }

    private Set<String> resolved(Set<String> requested, Set<String> available) {
        return requested.stream()
                .filter(available::contains)
                .collect(Collectors.toUnmodifiableSet());
    }
}
