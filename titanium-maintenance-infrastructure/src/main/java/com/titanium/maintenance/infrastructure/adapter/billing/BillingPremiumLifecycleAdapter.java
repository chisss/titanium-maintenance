package com.titanium.maintenance.infrastructure.adapter.billing;

import org.springframework.stereotype.Component;

import com.titanium.billing.api.request.retroactive.PostPremiumLifecycleAdjustmentRequest;
import com.titanium.billing.api.request.retroactive.ReversePremiumLifecyclePostingRequest;
import com.titanium.billing.api.response.retroactive.PremiumLifecyclePostingResponse;
import com.titanium.billing.api.response.retroactive.PremiumLifecycleReversalResponse;
import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.exception.MaintenanceRemoteCallException;
import com.titanium.maintenance.infrastructure.client.billing.BillingPremiumLifecycleClient;
import com.titanium.maintenance.port.billing.BillingPremiumLifecyclePort;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.response.ApiResponse;

import lombok.RequiredArgsConstructor;

/** Billing 生命周期余额端口的防腐适配器。 */
@Component
@RequiredArgsConstructor
public class BillingPremiumLifecycleAdapter implements BillingPremiumLifecyclePort {

    private static final String BILLING_REVERSAL_POSTED = "POSTED";
    private static final String MAINTENANCE_REVERSED = "REVERSED";

    private final BillingPremiumLifecycleClient client;

    @Override
    public PostingFact post(PostingRequest request) {
        ApiResponse<PremiumLifecyclePostingResponse> apiResponse = client.post(
                new PostPremiumLifecycleAdjustmentRequest(
                        request.adjustmentId(), request.adjustmentResultHash(), request.policyId(),
                        request.customerId(), request.createdBy(), request.tenantId()), request.tenantId());
        if (apiResponse == null || !apiResponse.isSuccess() || apiResponse.getData() == null) {
            String detail = apiResponse == null
                    ? "空响应"
                    : apiResponse.getCode() + ":" + apiResponse.getMessage();
            throw new MaintenanceRemoteCallException("Billing 生命周期入账失败: " + detail,
                    MaintenanceErrorCode.MAINTENANCE_BILLING_REMOTE_ERROR);
        }
        PremiumLifecyclePostingResponse response = apiResponse.getData();
        return new PostingFact(
                response.postingId(), response.adjustmentId(), response.resultHash(),
                MaintenanceBalanceDirection.fromCode(response.direction()), response.amount(), response.currency(),
                response.status(), response.refundInstructionId(), response.refundOrderId(), response.refundStatus(),
                response.commissionAdjustmentCount());
    }

    @Override
    public ReversalFact reverse(ReversalRequest request) {
        ApiResponse<PremiumLifecycleReversalResponse> apiResponse = client.reverse(
                request.sourcePostingId(),
                new ReversePremiumLifecyclePostingRequest(
                        request.requestId(), request.reason(), request.createdBy()),
                request.tenantId());
        if (apiResponse == null || !apiResponse.isSuccess() || apiResponse.getData() == null) {
            String detail = apiResponse == null
                    ? "空响应"
                    : apiResponse.getCode() + ":" + apiResponse.getMessage();
            throw new MaintenanceRemoteCallException("Billing 生命周期冲正失败: " + detail,
                    MaintenanceErrorCode.MAINTENANCE_BILLING_REVERSAL_REMOTE_ERROR);
        }
        PremiumLifecycleReversalResponse response = apiResponse.getData();
        if (!BILLING_REVERSAL_POSTED.equals(response.status())) {
            throw new MaintenanceRemoteCallException(
                    "Billing 生命周期冲正状态无效: " + response.status(),
                    MaintenanceErrorCode.MAINTENANCE_BILLING_REVERSAL_CONTRACT_INVALID);
        }
        return new ReversalFact(
                response.reversalId(), response.requestId(), response.requestHash(), response.resultHash(),
                response.sourcePostingId(), response.sourceResultHash(), response.policyId(), response.customerId(),
                MaintenanceBalanceDirection.fromCode(response.direction()), response.amount(), response.currency(),
                MAINTENANCE_REVERSED, response.createdAt());
    }
}
