package com.titanium.maintenance.infrastructure.adapter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.titanium.billing.api.response.BillResponse;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactDomain;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactItemStatus;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactSeverity;
import com.titanium.maintenance.common.enums.workflow.MaintenanceRetroactiveImpactType;
import com.titanium.maintenance.infrastructure.client.BillingRetroactiveImpactClient;
import com.titanium.maintenance.port.MaintenanceRetroactiveImpactSourcePort;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactiveImpactItem;
import com.titanium.metadata.response.ApiResponse;

import lombok.RequiredArgsConstructor;

/** Billing 账单与续期追溯影响取证适配器。 */
@Component
@RequiredArgsConstructor
public class BillingRetroactiveImpactSourceAdapter
        extends AbstractRetroactiveImpactSourceAdapter<BillResponse> {

    private static final String ITEM_EVIDENCE_VERSION = "BILL_V1";

    private final BillingRetroactiveImpactClient client;

    @Override
    public MaintenanceRetroactiveImpactDomain sourceDomain() {
        return MaintenanceRetroactiveImpactDomain.BILLING;
    }

    @Override
    protected ApiResponse<List<BillResponse>> query(ImpactRequest request) {
        return client.getBillsByPolicyId(request.policyId(), request.tenantId());
    }

    @Override
    protected List<MaintenanceRetroactiveImpactItem> toItems(ImpactRequest request, BillResponse source) {
        requirePolicy(request, source.getPolicyId());
        String billId = requireText("billId", source.getBillId());
        String billingType = requireText("billingType", source.getBillingType());
        String status = requireText("status", source.getStatus());
        LocalDateTime occurredAt = source.getIssueDate() == null ? source.getCreatedAt()
                : source.getIssueDate().atStartOfDay();
        MaintenanceRetroactiveImpactType impactType = billingType.toUpperCase(Locale.ROOT).contains("RENEW")
                ? MaintenanceRetroactiveImpactType.RENEWAL
                : MaintenanceRetroactiveImpactType.PREMIUM_BILL;
        BigDecimal amount = source.getAmount();
        String currency = amount == null ? null : requireText("currency", source.getCurrency());
        MaintenanceRetroactiveImpactSeverity severity = source.getPaidAmount() != null
                && source.getPaidAmount().signum() > 0
                        ? MaintenanceRetroactiveImpactSeverity.BLOCKING
                        : MaintenanceRetroactiveImpactSeverity.WARNING;
        String hash = MaintenanceRetroactiveImpactSourcePort.itemHash(
                billId, source.getPolicyId(), billingType, value(amount), currency,
                value(source.getPaidAmount()), value(source.getUnpaidAmount()), status,
                value(source.getIssueDate()), value(source.getDueDate()), value(source.getPaymentDate()),
                value(source.getCreatedAt()), value(source.getUpdatedAt()));
        return List.of(new MaintenanceRetroactiveImpactItem(
                "BILLING:" + billId, sourceDomain(), impactType, billId, billId,
                occurredAt, status, amount, currency, severity,
                MaintenanceRetroactiveImpactItemStatus.PENDING,
                impactType == MaintenanceRetroactiveImpactType.RENEWAL
                        ? "追溯时点后存在续期账单" : "追溯时点后存在保费账单",
                ITEM_EVIDENCE_VERSION, hash));
    }
}
