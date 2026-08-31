package com.titanium.maintenance.infrastructure.adapter;

import java.time.YearMonth;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.titanium.billing.api.RetroactivePeriodAdjustmentApi;
import com.titanium.billing.api.request.ResolveClosedRetroactivePeriodsRequest;
import com.titanium.billing.api.response.RetroactivePeriodAdjustmentResolutionResponse;
import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.exception.MaintenanceRemoteCallException;
import com.titanium.maintenance.port.BillingRetroactivePeriodResolutionPort;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.response.ApiResponse;

import lombok.RequiredArgsConstructor;

/** Billing 关闭会计期间处理正式契约防腐适配器。 */
@Component
@RequiredArgsConstructor
public class BillingRetroactivePeriodResolutionAdapter implements BillingRetroactivePeriodResolutionPort {

    private final RetroactivePeriodAdjustmentApi api;

    @Override
    public ResolutionFact resolve(ResolutionRequest request) {
        RetroactivePeriodAdjustmentResolutionResponse response = requireSuccess(
                api.resolveClosedPeriods(request.billingBatchId(), new ResolveClosedRetroactivePeriodsRequest(
                        request.resolutionRequestId(), request.targetAccountingPeriod().toString(),
                        request.reason(), request.operatorId(), request.tenantId()), request.tenantId()));
        validateEcho(request, response);
        return toFact(response);
    }

    @Override
    public ResolutionFact get(String tenantId, String billingBatchId) {
        return toFact(requireSuccess(api.getClosedPeriodResolution(billingBatchId, tenantId)));
    }

    private ResolutionFact toFact(RetroactivePeriodAdjustmentResolutionResponse response) {
        return new ResolutionFact(
                response.resolutionId(), response.resolutionRequestId(), response.batchId(),
                response.tenantId(), response.maintenanceId(), response.policyId(),
                response.sourceBatchResultHash(), YearMonth.parse(response.targetAccountingPeriod()),
                response.status(), response.resolvedLineCount(), response.requestHash(), response.resultHash(),
                response.reason(), response.resolvedBy(), response.resolvedAt(), response.lines().stream()
                        .map(line -> new ResolutionLineFact(
                                line.periodId(), YearMonth.parse(line.sourceAccountingPeriod()),
                                YearMonth.parse(line.targetAccountingPeriod()),
                                MaintenanceBalanceDirection.fromCode(line.direction()),
                                line.differenceAmount(), line.currency(), line.postingReference(),
                                line.sourceLineResultHash(), line.resultHash()))
                        .toList());
    }

    private void validateEcho(
            ResolutionRequest request,
            RetroactivePeriodAdjustmentResolutionResponse response) {
        if (!Objects.equals(request.resolutionRequestId(), response.resolutionRequestId())
                || !Objects.equals(request.billingBatchId(), response.batchId())
                || !Objects.equals(request.tenantId(), response.tenantId())
                || !Objects.equals(request.maintenanceId(), response.maintenanceId())
                || !Objects.equals(request.policyId(), response.policyId())
                || !Objects.equals(request.sourceBatchResultHash(), response.sourceBatchResultHash())
                || !Objects.equals(request.targetAccountingPeriod().toString(), response.targetAccountingPeriod())
                || !Objects.equals(request.reason(), response.reason())
                || !Objects.equals(request.operatorId(), response.resolvedBy())
                || !Objects.equals("COMPLETED", response.status())) {
            throw remoteError("Billing关闭期间处理回显不一致");
        }
    }

    private RetroactivePeriodAdjustmentResolutionResponse requireSuccess(
            ApiResponse<RetroactivePeriodAdjustmentResolutionResponse> response) {
        if (response == null || !response.isSuccess() || response.getData() == null) {
            throw remoteError("Billing未返回有效关闭期间处理结论");
        }
        return response.getData();
    }

    private MaintenanceRemoteCallException remoteError(String message) {
        return new MaintenanceRemoteCallException(message, MaintenanceErrorCode.MAINTENANCE_BILLING_RETROACTIVE_RESOLUTION_ERROR);
    }
}
