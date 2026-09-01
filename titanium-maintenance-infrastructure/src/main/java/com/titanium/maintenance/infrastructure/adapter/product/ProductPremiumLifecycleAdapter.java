package com.titanium.maintenance.infrastructure.adapter.product;

import java.util.List;

import org.springframework.stereotype.Component;

import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.exception.MaintenanceRemoteCallException;
import com.titanium.maintenance.infrastructure.client.product.ProductPremiumLifecycleClient;
import com.titanium.maintenance.port.product.ProductPremiumLifecyclePort;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.product.api.request.config.UnderwritingAdjustmentRequest;
import com.titanium.product.api.request.premium.PremiumCalculationRequest;
import com.titanium.product.api.request.premium.PremiumLifecycleAdjustmentRequest;
import com.titanium.product.api.request.premium.PremiumLifecycleReversalRequest;
import com.titanium.product.api.response.premium.PremiumCalculationResponse;
import com.titanium.product.api.response.premium.PremiumLifecycleAdjustmentResponse;

import lombok.RequiredArgsConstructor;

/** Product 生命周期计价端口的防腐适配器。 */
@Component
@RequiredArgsConstructor
public class ProductPremiumLifecycleAdapter implements ProductPremiumLifecyclePort {

    private final ProductPremiumLifecycleClient client;

    @Override
    public CalculationFact calculateReplacement(CalculationRequest request) {
        PremiumCalculationResponse response = requireSuccess(client.calculate(
                request.productId(), new PremiumCalculationRequest(
                        request.calculationRequestId(), request.bizNo(), "MAINTENANCE", request.productVersion(),
                        request.businessTime(), request.currency(), request.sumInsured(), request.age(),
                        request.gender(), request.paymentTermYears(), request.coverageTermYears(),
                        request.paymentPeriods(), request.requestSnapshot(), toApiAdjustments(request),
                        request.channelId(), request.policyYear()), request.tenantId()), "Product 替代计算");
        return new CalculationFact(
                response.calculationId(), response.calculationRequestId(), response.bizNo(), response.purpose(),
                response.productId(), response.productVersion(), response.currency(), response.resultHash());
    }

    @Override
    public AdjustmentFact createAdjustment(AdjustmentRequest request) {
        PremiumLifecycleAdjustmentResponse response = requireSuccess(client.createAdjustment(
                new PremiumLifecycleAdjustmentRequest(
                        request.adjustmentRequestId(), request.bizNo(), request.lifecycleType(),
                        request.originalCalculationId(), request.replacementCalculationId(),
                        request.businessTime(), request.reason()), request.tenantId()), "Product 生命周期差额");
        return new AdjustmentFact(
                response.adjustmentId(), response.adjustmentRequestId(), response.originalCalculationId(),
                response.replacementCalculationId(), response.resultHash(),
                MaintenanceBalanceDirection.fromCode(response.direction()), response.customerAmount(),
                response.currency());
    }

    @Override
    public AdjustmentFact createReversal(ReversalRequest request) {
        PremiumLifecycleAdjustmentResponse response = requireSuccess(client.createReversal(
                new PremiumLifecycleReversalRequest(request.adjustmentRequestId(), request.sourceAdjustmentId(),
                        request.businessTime(), request.reason()), request.tenantId()), "Product 生命周期冲正");
        return new AdjustmentFact(
                response.adjustmentId(), response.adjustmentRequestId(), response.originalCalculationId(),
                response.replacementCalculationId(), response.resultHash(),
                MaintenanceBalanceDirection.fromCode(response.direction()), response.customerAmount(),
                response.currency());
    }

    private List<UnderwritingAdjustmentRequest> toApiAdjustments(CalculationRequest request) {
        if (request.underwritingAdjustments() == null) {
            return List.of();
        }
        return request.underwritingAdjustments().stream()
                .map(item -> new UnderwritingAdjustmentRequest(
                        item.adjustmentCode(), item.type(), item.value(), item.reason(), item.ruleVersion()))
                .toList();
    }

    private <T> T requireSuccess(ApiResponse<T> response, String action) {
        if (response == null || !response.isSuccess() || response.getData() == null) {
            String detail = response == null ? "空响应" : response.getCode() + ":" + response.getMessage();
            throw new MaintenanceRemoteCallException(action + "失败: " + detail,
                    MaintenanceErrorCode.MAINTENANCE_PRODUCT_REMOTE_ERROR);
        }
        return response.getData();
    }
}
