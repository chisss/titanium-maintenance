package com.titanium.maintenance.infrastructure.adapter.product;

import org.springframework.stereotype.Component;

import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.exception.MaintenanceRemoteCallException;
import com.titanium.maintenance.infrastructure.client.product.ProductSurrenderValueClient;
import com.titanium.maintenance.port.product.ProductSurrenderValuePort;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.product.api.request.premium.SurrenderValueCalculationRequest;
import com.titanium.product.api.response.premium.SurrenderValueCalculationResponse;

import lombok.RequiredArgsConstructor;

/** Product 退保价值端口的防腐适配器。 */
@Component
@RequiredArgsConstructor
public class ProductSurrenderValueAdapter implements ProductSurrenderValuePort {

    private final ProductSurrenderValueClient client;

    @Override
    public SurrenderFact calculate(SurrenderRequest request) {
        ApiResponse<SurrenderValueCalculationResponse> apiResponse = client.calculate(
                new SurrenderValueCalculationRequest(
                        request.surrenderRequestId(), request.bizNo(), request.originalBizNo(),
                        request.originalCalculationId(),
                        request.policyEffectiveDate(), request.surrenderDate(), request.policyYear(),
                        request.businessTime(), request.reason()), request.tenantId());
        if (apiResponse == null || !apiResponse.isSuccess() || apiResponse.getData() == null) {
            String detail = apiResponse == null ? "空响应" : apiResponse.getCode() + ":" + apiResponse.getMessage();
            throw new MaintenanceRemoteCallException(
                    "Product 退保价值计算失败: " + detail,
                    MaintenanceErrorCode.MAINTENANCE_SURRENDER_REMOTE_ERROR);
        }
        SurrenderValueCalculationResponse response = apiResponse.getData();
        return new SurrenderFact(
                response.surrenderRequestId(), response.policyCode(), response.policyVersion(),
                response.policyContentHash(), response.policyYear(), response.coolingOffDays(),
                response.refundType(), response.withinCoolingOff(), response.cashValueRate(),
                response.refundAmount(), response.retainedCustomerAmount(),
                response.internalCostRetentionRate(), response.originalCalculationId(), response.originalResultHash(),
                response.replacementCalculationId(), response.replacementResultHash(),
                response.adjustmentId(), response.requestHash(), response.adjustmentResultHash(),
                response.pricingPlanVersion(), response.pricingPlanContentHash(),
                MaintenanceBalanceDirection.fromCode(response.direction()), response.amount(), response.currency());
    }
}
