package com.titanium.maintenance.infrastructure.adapter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.titanium.claim.api.response.ClaimResponse;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactDomain;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactItemStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactSeverity;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactType;
import com.titanium.maintenance.infrastructure.client.ClaimRetroactiveImpactClient;
import com.titanium.maintenance.port.MaintenanceRetroactiveImpactSourcePort;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactiveImpactItem;
import com.titanium.metadata.response.ApiResponse;

import lombok.RequiredArgsConstructor;

/** Claim 理赔与给付追溯影响取证适配器。 */
@Component
@RequiredArgsConstructor
public class ClaimRetroactiveImpactSourceAdapter
        extends AbstractRetroactiveImpactSourceAdapter<ClaimResponse> {

    private static final String ITEM_EVIDENCE_VERSION = "CLAIM_V1";

    private final ClaimRetroactiveImpactClient client;

    @Override
    public MaintenanceRetroactiveImpactDomain sourceDomain() {
        return MaintenanceRetroactiveImpactDomain.CLAIM;
    }

    @Override
    protected ApiResponse<List<ClaimResponse>> query(ImpactRequest request) {
        return client.getClaimsByPolicyId(request.policyId(), request.tenantId());
    }

    @Override
    protected List<MaintenanceRetroactiveImpactItem> toItems(ImpactRequest request, ClaimResponse source) {
        requirePolicy(request, source.getPolicyId());
        String claimId = requireText("claimId", source.getClaimId());
        String claimType = requireText("claimType", source.getClaimType());
        String status = requireText("status", source.getStatus());
        LocalDateTime occurredAt = source.getIncidentDate() == null ? source.getCreatedAt() : source.getIncidentDate();
        MaintenanceRetroactiveImpactType impactType = isBenefit(claimType)
                ? MaintenanceRetroactiveImpactType.BENEFIT
                : MaintenanceRetroactiveImpactType.CLAIM;
        String hash = MaintenanceRetroactiveImpactSourcePort.itemHash(
                claimId, source.getPolicyId(), source.getClaimNumber(), claimType,
                value(source.getIncidentDate()), value(source.getClaimAmount()), status,
                value(source.getCreatedAt()), value(source.getUpdatedAt()));
        return List.of(new MaintenanceRetroactiveImpactItem(
                "CLAIM:" + claimId, sourceDomain(), impactType, claimId, source.getClaimNumber(),
                occurredAt, status, null, null, MaintenanceRetroactiveImpactSeverity.BLOCKING,
                MaintenanceRetroactiveImpactItemStatus.PENDING,
                impactType == MaintenanceRetroactiveImpactType.BENEFIT
                        ? "追溯时点后存在保险给付案件" : "追溯时点后存在理赔案件",
                ITEM_EVIDENCE_VERSION, hash));
    }

    private boolean isBenefit(String claimType) {
        String normalized = claimType.toUpperCase(Locale.ROOT);
        return normalized.contains("DEATH") || normalized.contains("BENEFIT")
                || normalized.contains("ANNUITY");
    }
}
