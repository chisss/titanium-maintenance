package com.titanium.maintenance.infrastructure.adapter.retroactive;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;

import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactDomain;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactItemStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactSeverity;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactType;
import com.titanium.maintenance.infrastructure.client.policy.PolicyServiceClient;
import com.titanium.maintenance.port.maintenance.MaintenanceRetroactiveImpactSourcePort;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactiveImpactItem;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.api.response.PolicyEndorsementResponse;

import lombok.RequiredArgsConstructor;

/** Policy 后续批单追溯影响取证适配器。 */
@Component
@RequiredArgsConstructor
public class PolicyRetroactiveImpactSourceAdapter
        extends AbstractRetroactiveImpactSourceAdapter<PolicyEndorsementResponse> {

    private static final String ITEM_EVIDENCE_VERSION = "POLICY_ENDORSEMENT_V1";

    private final PolicyServiceClient client;

    @Override
    public MaintenanceRetroactiveImpactDomain sourceDomain() {
        return MaintenanceRetroactiveImpactDomain.POLICY;
    }

    @Override
    protected ApiResponse<List<PolicyEndorsementResponse>> query(ImpactRequest request) {
        return client.getEndorsements(request.policyId(), request.tenantId());
    }

    @Override
    protected List<MaintenanceRetroactiveImpactItem> toItems(
            ImpactRequest request,
            PolicyEndorsementResponse source) {
        requirePolicy(request, source.policyId());
        requireTenant(request, source.tenantId());
        String endorsementNo = requireText("endorsementNo", source.endorsementNo());
        LocalDateTime occurredAt = source.effectiveDate() != null ? source.effectiveDate() : source.endorsedAt();
        String hash = MaintenanceRetroactiveImpactSourcePort.itemHash(
                endorsementNo, source.policyId(), source.updateType(), source.category(),
                value(source.policyVersion()), value(source.effectiveDate()), source.changeSummary(),
                value(source.requiresPremiumRecalc()), source.sourceMaintenanceId(),
                source.operatorId(), value(source.endorsedAt()), source.tenantId());
        return List.of(new MaintenanceRetroactiveImpactItem(
                "POLICY:" + endorsementNo, sourceDomain(),
                MaintenanceRetroactiveImpactType.SUBSEQUENT_ENDORSEMENT,
                endorsementNo, endorsementNo, occurredAt, "ENDORSED", null, null,
                MaintenanceRetroactiveImpactSeverity.BLOCKING,
                MaintenanceRetroactiveImpactItemStatus.PENDING,
                "追溯时点后存在已落地批单", ITEM_EVIDENCE_VERSION, hash));
    }
}
