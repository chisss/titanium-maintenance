package com.titanium.maintenance.infrastructure.adapter;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.exception.MaintenanceRemoteCallException;
import com.titanium.maintenance.port.ProductRetroactivePeriodRecalculationPort;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactiveProductPeriodDifference;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.product.api.ProductPremiumCalculationApi;
import com.titanium.product.api.request.RetroactivePremiumPeriodRecalculationRequest;
import com.titanium.product.api.request.RetroactivePremiumPeriodRecalculationRequest.AffectedPeriodRequest;
import com.titanium.product.api.response.RetroactivePremiumPeriodRecalculationResponse;

import lombok.RequiredArgsConstructor;

/** Product 追溯期间重算正式契约防腐适配器。 */
@Component
@RequiredArgsConstructor
public class ProductRetroactivePeriodRecalculationAdapter
        implements ProductRetroactivePeriodRecalculationPort {

    private final ProductPremiumCalculationApi api;

    @Override
    public RecalculationFact recalculate(RecalculationRequest request) {
        RetroactivePremiumPeriodRecalculationResponse response = requireSuccess(
                api.recalculateRetroactivePeriods(toApiRequest(request), request.tenantId()));
        validateEcho(request, response);
        return new RecalculationFact(
                response.tenantId(), response.maintenanceId(), response.policyId(), response.analysisId(),
                response.analysisVersion(), response.analysisResultHash(), response.recalculationId(),
                response.recalculationVersion(), response.recalculationRequestId(),
                response.originalCalculationId(), response.originalResultHash(),
                response.replacementCalculationId(), response.replacementResultHash(),
                MaintenanceBalanceDirection.fromCode(response.direction()), response.amount(), response.currency(),
                response.inputHash(), response.resultHash(), response.calculatedAt(), response.periods().stream()
                        .map(period -> new MaintenanceRetroactiveProductPeriodDifference(
                                period.periodId(), period.sourceReferenceId(), period.periodStart(),
                                period.originalAmount(), period.recalculatedAmount(),
                                MaintenanceBalanceDirection.fromCode(period.direction()),
                                period.differenceAmount(), period.currency(), period.sourceEvidenceHash(),
                                period.resultHash()))
                        .toList());
    }

    private RetroactivePremiumPeriodRecalculationRequest toApiRequest(RecalculationRequest request) {
        return new RetroactivePremiumPeriodRecalculationRequest(
                request.requestId(), request.maintenanceId(), request.policyId(), request.analysisId(),
                request.analysisVersion(), request.analysisResultHash(), request.originalCalculationId(),
                request.replacementCalculationId(), request.scopeFrom(), request.scopeTo(),
                request.periods().stream().map(period -> new AffectedPeriodRequest(
                        period.periodId(), period.sourceReferenceId(), period.periodStart(),
                        period.originalAmount(), period.currency(), period.sourceEvidenceHash())).toList());
    }

    private void validateEcho(
            RecalculationRequest request,
            RetroactivePremiumPeriodRecalculationResponse response) {
        if (!Objects.equals(request.tenantId(), response.tenantId())
                || !Objects.equals(request.maintenanceId(), response.maintenanceId())
                || !Objects.equals(request.policyId(), response.policyId())
                || !Objects.equals(request.analysisId(), response.analysisId())
                || request.analysisVersion() != response.analysisVersion()
                || !Objects.equals(request.analysisResultHash(), response.analysisResultHash())
                || !Objects.equals(request.requestId(), response.recalculationRequestId())
                || !Objects.equals(request.originalCalculationId(), response.originalCalculationId())
                || !Objects.equals(request.replacementCalculationId(), response.replacementCalculationId())
                || !Objects.equals(request.scopeFrom(), response.scopeFrom())
                || !Objects.equals(request.scopeTo(), response.scopeTo())
                || request.periods().size() != response.periods().size()) {
            throw remoteError("Product追溯期间重算回显不一致");
        }
        request.periods().forEach(period -> response.periods().stream()
                .filter(result -> result.periodId().equals(period.periodId()))
                .filter(result -> result.sourceReferenceId().equals(period.sourceReferenceId()))
                .filter(result -> result.periodStart().equals(period.periodStart()))
                .filter(result -> result.originalAmount().compareTo(period.originalAmount()) == 0)
                .filter(result -> result.currency().equalsIgnoreCase(period.currency()))
                .filter(result -> result.sourceEvidenceHash().equals(period.sourceEvidenceHash()))
                .findFirst().orElseThrow(() -> remoteError("Product追溯期间重算明细回显不一致")));
    }

    private RetroactivePremiumPeriodRecalculationResponse requireSuccess(
            ApiResponse<RetroactivePremiumPeriodRecalculationResponse> response) {
        if (response == null || !response.isSuccess() || response.getData() == null) {
            throw remoteError("Product未返回有效追溯期间重算结果");
        }
        return response.getData();
    }

    private MaintenanceRemoteCallException remoteError(String message) {
        return new MaintenanceRemoteCallException(message, MaintenanceErrorCode.MAINTENANCE_PRODUCT_RETROACTIVE_RECALCULATION_ERROR);
    }
}
