package com.titanium.maintenance.infrastructure.adapter;

import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import com.titanium.maintenance.common.enums.MaintenanceBalanceDirection;
import com.titanium.maintenance.common.exception.BusinessException;
import com.titanium.maintenance.common.exception.MaintenanceRemoteCallException;
import com.titanium.maintenance.port.ProductMaintenancePremiumQuotePort;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.product.api.ProductMaintenancePremiumQuoteApi;
import com.titanium.product.api.request.MaintenancePremiumQuoteRequest;
import com.titanium.product.api.request.MaintenancePremiumQuoteRequest.SnapshotReferenceRequest;
import com.titanium.product.api.request.UnderwritingAdjustmentRequest;
import com.titanium.product.api.response.MaintenancePremiumQuoteResponse;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Product 保全版本化报价正式契约的防腐适配器。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductMaintenancePremiumQuoteAdapter implements ProductMaintenancePremiumQuotePort {

    private final ProductMaintenancePremiumQuoteApi api;

    @Override
    public QuoteFact quote(QuoteRequest request) {
        try {
            MaintenancePremiumQuoteResponse response = requireSuccess(
                    api.quote(request.productId(), toApiRequest(request), request.tenantId()));
            validateEcho(request, response);
            return new QuoteFact(
                    response.tenantId(), response.maintenanceId(), response.policyId(),
                    response.policyBaselineVersion(), response.productId(), response.productVersion(),
                    response.planVersion(), response.itemCode(), response.beforeSnapshotHash(),
                    response.proposedSnapshotHash(), response.quoteId(), response.quoteVersion(),
                    response.originalCalculationId(), response.originalResultHash(),
                    response.replacementCalculationId(), response.replacementResultHash(),
                    response.pricingPlanVersion(), response.pricingPlanContentHash(),
                    response.idempotencyKey(), response.payloadHash(), response.resultHash(),
                    response.detailSummary(), MaintenanceBalanceDirection.fromCode(response.direction()),
                    response.amount(), response.currency(), response.quotedAt(), response.validUntil());
        } catch (BusinessException exception) {
            throw exception;
        } catch (FeignException exception) {
            log.warn("调用 Product 保全报价失败, tenantId={}, maintenanceId={}, itemCode={}, status={}",
                    request.tenantId(), request.maintenanceId(), request.itemCode(), exception.status());
            throw feignFailure(exception);
        } catch (RuntimeException exception) {
            log.warn("调用 Product 保全报价失败, tenantId={}, maintenanceId={}, itemCode={}, errorType={}",
                    request.tenantId(), request.maintenanceId(), request.itemCode(),
                    exception.getClass().getSimpleName());
            throw remoteError("Product 保全报价不可用或契约无效");
        }
    }

    private MaintenancePremiumQuoteRequest toApiRequest(QuoteRequest request) {
        return new MaintenancePremiumQuoteRequest(
                request.maintenanceId(), request.policyId(), request.policyBaselineVersion(),
                request.itemCode(), request.productVersion(), request.planVersion(), request.lifecycleType(),
                snapshot(request.beforeSnapshot()), snapshot(request.proposedSnapshot()),
                request.originalCalculationId(), request.businessTime(), request.currency(),
                request.sumInsured(), request.age(), request.gender(), request.paymentTermYears(),
                request.coverageTermYears(), request.paymentPeriods(), request.pricingFactors(),
                request.underwritingAdjustments().stream()
                        .map(item -> new UnderwritingAdjustmentRequest(
                                item.adjustmentCode(), item.type(), item.value(),
                                item.reason(), item.ruleVersion()))
                        .toList(),
                request.channelId(), request.policyYear(), request.reason(),
                request.idempotencyKey(), request.payloadHash());
    }

    private SnapshotReferenceRequest snapshot(SnapshotReference reference) {
        return new SnapshotReferenceRequest(
                reference.storageKey(), reference.contentHash(), reference.policyVersion(), reference.capturedAt());
    }

    private MaintenancePremiumQuoteResponse requireSuccess(
            ApiResponse<MaintenancePremiumQuoteResponse> response) {
        if (response == null) {
            throw remoteError("Product 未返回有效保全报价结果");
        }
        if (!response.isSuccess()) {
            throw downstreamError(
                    response.getMessage(), response.getCode(), HttpStatus.UNPROCESSABLE_CONTENT);
        }
        if (response.getData() == null) {
            throw remoteError("Product 未返回有效保全报价结果");
        }
        return response.getData();
    }

    private BusinessException feignFailure(FeignException exception) {
        try {
            JSONObject body = JSON.parseObject(exception.contentUTF8());
            String errorCode = body == null ? null : body.getString("code");
            String message = body == null ? null : body.getString("message");
            HttpStatus status = HttpStatus.resolve(exception.status());
            if (hasText(errorCode) && hasText(message) && status != null) {
                return downstreamError(message, errorCode, status);
            }
        } catch (RuntimeException parseFailure) {
            log.debug("解析 Product 保全报价错误响应失败", parseFailure);
        }
        return remoteError("Product 保全报价不可用或契约无效");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void validateEcho(QuoteRequest request, MaintenancePremiumQuoteResponse response) {
        if (!Objects.equals(request.tenantId(), response.tenantId())
                || !Objects.equals(request.maintenanceId(), response.maintenanceId())
                || !Objects.equals(request.policyId(), response.policyId())
                || !Objects.equals(request.policyBaselineVersion(), response.policyBaselineVersion())
                || !Objects.equals(request.productId(), response.productId())
                || !Objects.equals(request.productVersion(), response.productVersion())
                || !Objects.equals(request.planVersion(), response.planVersion())
                || !Objects.equals(request.itemCode(), response.itemCode())
                || !Objects.equals(request.beforeSnapshot().contentHash(), response.beforeSnapshotHash())
                || !Objects.equals(request.proposedSnapshot().contentHash(), response.proposedSnapshotHash())
                || !Objects.equals(request.originalCalculationId(), response.originalCalculationId())
                || !Objects.equals(request.idempotencyKey(), response.idempotencyKey())
                || !Objects.equals(request.payloadHash(), response.payloadHash())) {
            throw remoteError("Product 保全报价结果回显不一致");
        }
    }

    private MaintenanceRemoteCallException remoteError(String message) {
        return new MaintenanceRemoteCallException(message, MaintenanceErrorCode.MAINTENANCE_PRODUCT_QUOTE_REMOTE_ERROR);
    }

    /**
     * 下游拒绝信息收敛为本域业务异常。
     * <p>下游字符串错误码不允许作为本域业务错误码外泄（红线 19），统一携带
     * {@code MAINTENANCE_PRODUCT_QUOTE_REMOTE_ERROR}，下游码与 HTTP 状态并入消息供排查。</p>
     */
    private BusinessException downstreamError(String message, String errorCode, HttpStatus status) {
        if (!hasText(message) || !hasText(errorCode)) {
            return remoteError("Product 未返回有效保全报价结果");
        }
        return new BusinessException(
                "Product 保全报价下游拒绝: " + message + " [code=" + errorCode
                        + ", httpStatus=" + (status == null ? "unknown" : status.value()) + "]",
                MaintenanceErrorCode.MAINTENANCE_PRODUCT_QUOTE_REMOTE_ERROR);
    }
}
