package com.titanium.maintenance.infrastructure.adapter;

import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.titanium.billing.api.RetroactivePeriodAdjustmentApi;
import com.titanium.billing.api.request.PostRetroactivePeriodAdjustmentRequest;
import com.titanium.billing.api.request.PostRetroactivePeriodAdjustmentRequest.PeriodAdjustmentRequest;
import com.titanium.billing.api.response.RetroactivePeriodAdjustmentResponse;
import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.exception.BusinessException;
import com.titanium.maintenance.port.BillingRetroactivePeriodAdjustmentPort;
import com.titanium.maintenance.valueobject.workflow.MaintenanceRetroactiveBillingPeriodAdjustment;
import com.titanium.metadata.response.ApiResponse;

import lombok.RequiredArgsConstructor;

/** Billing 追溯期间调整正式契约防腐适配器。 */
@Component
@RequiredArgsConstructor
public class BillingRetroactivePeriodAdjustmentAdapter implements BillingRetroactivePeriodAdjustmentPort {

    private final RetroactivePeriodAdjustmentApi api;

    @Override
    public AdjustmentFact adjust(AdjustmentRequest request) {
        RetroactivePeriodAdjustmentResponse response = requireSuccess(
                api.post(toApiRequest(request), request.tenantId()));
        validateEcho(request, response);
        return new AdjustmentFact(
                response.tenantId(), response.maintenanceId(), response.policyId(), response.customerId(),
                response.analysisId(), response.analysisVersion(), response.analysisResultHash(),
                response.recalculationId(), response.recalculationVersion(), response.productInputHash(),
                response.productResultHash(), response.batchId(), response.status(), response.postedCount(),
                response.reviewCount(), response.requestHash(), response.resultHash(), response.createdAt(),
                response.periods().stream().map(period -> new MaintenanceRetroactiveBillingPeriodAdjustment(
                        period.periodId(), period.sourceReferenceId(), period.accountingPeriod(),
                        period.periodStart(), period.originalAmount(), period.recalculatedAmount(),
                        MaintenanceBalanceDirection.fromCode(period.direction()), period.differenceAmount(),
                        period.currency(), period.status(), period.sourceEvidenceHash(),
                        period.productResultHash(), period.resultHash())).toList());
    }

    private PostRetroactivePeriodAdjustmentRequest toApiRequest(AdjustmentRequest request) {
        var product = request.productEvidence();
        return new PostRetroactivePeriodAdjustmentRequest(
                request.adjustmentRequestId(), request.maintenanceId(), request.policyId(), request.customerId(),
                request.analysisId(), request.analysisVersion(), request.analysisResultHash(),
                product.recalculationId(), product.recalculationVersion(), product.inputHash(),
                product.resultHash(), request.operatorId(), request.tenantId(), product.periods().stream()
                        .map(period -> new PeriodAdjustmentRequest(
                                period.periodId(), period.sourceReferenceId(), period.periodStart(),
                                period.originalAmount(), period.recalculatedAmount(), period.direction().getCode(),
                                period.differenceAmount(), period.currency(), period.sourceEvidenceHash(),
                                period.resultHash()))
                        .toList());
    }

    private void validateEcho(AdjustmentRequest request, RetroactivePeriodAdjustmentResponse response) {
        var product = request.productEvidence();
        if (!Objects.equals(request.tenantId(), response.tenantId())
                || !Objects.equals(request.maintenanceId(), response.maintenanceId())
                || !Objects.equals(request.policyId(), response.policyId())
                || !Objects.equals(request.customerId(), response.customerId())
                || !Objects.equals(request.analysisId(), response.analysisId())
                || request.analysisVersion() != response.analysisVersion()
                || !Objects.equals(request.analysisResultHash(), response.analysisResultHash())
                || !Objects.equals(product.recalculationId(), response.recalculationId())
                || !Objects.equals(product.recalculationVersion(), response.recalculationVersion())
                || !Objects.equals(product.inputHash(), response.productInputHash())
                || !Objects.equals(product.resultHash(), response.productResultHash())
                || product.periods().size() != response.periods().size()) {
            throw remoteError("Billing追溯期间调整回显不一致");
        }
        product.periods().forEach(period -> response.periods().stream()
                .filter(result -> result.periodId().equals(period.periodId()))
                .filter(result -> result.sourceReferenceId().equals(period.sourceReferenceId()))
                .filter(result -> result.productResultHash().equals(period.resultHash()))
                .findFirst().orElseThrow(() -> remoteError("Billing追溯期间调整明细回显不一致")));
    }

    private RetroactivePeriodAdjustmentResponse requireSuccess(
            ApiResponse<RetroactivePeriodAdjustmentResponse> response) {
        if (response == null || !response.isSuccess() || response.getData() == null) {
            throw remoteError("Billing未返回有效追溯期间调整结果");
        }
        return response.getData();
    }

    private BusinessException remoteError(String message) {
        return new BusinessException(message, "MAINTENANCE_BILLING_RETROACTIVE_ADJUSTMENT_ERROR",
                HttpStatus.BAD_GATEWAY);
    }
}
