package com.titanium.maintenance.infrastructure.adapter;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import com.titanium.maintenance.common.enums.ProductMaintenanceOfferingFailureReason;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.common.exception.ProductMaintenanceOfferingException;
import com.titanium.maintenance.infrastructure.client.ProductMaintenanceOfferingClient;
import com.titanium.maintenance.port.ProductMaintenanceOfferingPort;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.product.api.response.ProductMaintenanceOfferingResolutionResponse;

import feign.FeignException;
import lombok.RequiredArgsConstructor;

/** 通过 Product 正式 API 解析保全 Offering 的真实基础设施适配器。 */
@Component
@RequiredArgsConstructor
public class ProductMaintenanceOfferingAdapter implements ProductMaintenanceOfferingPort {

    private static final String ERROR_NOT_FOUND = "60000200";
    private static final String ERROR_VERSION_MISMATCH = "60000202";
    private static final String ERROR_NOT_APPLICABLE = "60000203";
    private static final String ERROR_CONTRACT_INVALID = "60000206";

    private final ProductMaintenanceOfferingClient client;

    @Override
    public ProductMaintenanceOfferingEvidence resolve(ProductMaintenanceOfferingRequest request) {
        try {
            ApiResponse<ProductMaintenanceOfferingResolutionResponse> response = client.resolve(
                    request.productId(), request.productVersion(), request.planVersion(),
                    request.policyStatus().getCode(), request.source().getCode(),
                    request.businessEffectiveAt(), request.tenantId());
            if (response == null) {
                throw failure(ProductMaintenanceOfferingFailureReason.UNAVAILABLE,
                        "Product保全Offering服务未返回响应");
            }
            if (!response.isSuccess()) {
                throw responseFailure(response.getCode());
            }
            return toEvidence(request, response.getData());
        } catch (ProductMaintenanceOfferingException exception) {
            throw exception;
        } catch (FeignException exception) {
            throw responseFailure(feignErrorCode(exception));
        } catch (MaintenanceValidationException | IllegalArgumentException exception) {
            throw failure(ProductMaintenanceOfferingFailureReason.CONTRACT_INVALID,
                    "Product保全Offering契约校验失败", exception);
        } catch (RuntimeException exception) {
            throw failure(ProductMaintenanceOfferingFailureReason.UNAVAILABLE,
                    "Product保全Offering服务不可用", exception);
        }
    }

    private ProductMaintenanceOfferingEvidence toEvidence(
            ProductMaintenanceOfferingRequest request,
            ProductMaintenanceOfferingResolutionResponse response) {
        if (response == null) {
            throw failure(ProductMaintenanceOfferingFailureReason.CONTRACT_INVALID,
                    "Product保全Offering响应体为空");
        }
        if (!Objects.equals(request.tenantId(), response.tenantId())
                || !Objects.equals(request.productId(), response.productId())) {
            throw failure(ProductMaintenanceOfferingFailureReason.CONTRACT_INVALID,
                    "Product保全Offering租户或产品回显不一致");
        }
        if (!Objects.equals(request.productVersion(), response.productVersion())
                || !Objects.equals(request.planVersion(), response.planVersion())) {
            throw failure(ProductMaintenanceOfferingFailureReason.VERSION_MISMATCH,
                    "Product保全Offering产品或计划版本回显不一致");
        }
        return new ProductMaintenanceOfferingEvidence(
                response.tenantId(), response.productId(), response.productVersion(), response.planVersion(),
                response.offeringId(), response.offeringVersion(), response.contentHash(),
                response.resolvedAt(), response.allowedItemCodes());
    }

    private ProductMaintenanceOfferingException responseFailure(String errorCode) {
        if (ERROR_NOT_FOUND.equals(errorCode)) {
            return failure(ProductMaintenanceOfferingFailureReason.NOT_FOUND,
                    "Product保全Offering不存在");
        }
        if (ERROR_VERSION_MISMATCH.equals(errorCode)) {
            return failure(ProductMaintenanceOfferingFailureReason.VERSION_MISMATCH,
                    "Product保全Offering版本不匹配");
        }
        if (ERROR_NOT_APPLICABLE.equals(errorCode)) {
            return failure(ProductMaintenanceOfferingFailureReason.NOT_APPLICABLE,
                    "Product保全Offering不适用于当前案件");
        }
        if (ERROR_CONTRACT_INVALID.equals(errorCode)) {
            return failure(ProductMaintenanceOfferingFailureReason.CONTRACT_INVALID,
                    "Product保全Offering契约无效");
        }
        return failure(ProductMaintenanceOfferingFailureReason.UNAVAILABLE,
                "Product保全Offering服务调用失败");
    }

    private String feignErrorCode(FeignException exception) {
        try {
            JSONObject body = JSON.parseObject(exception.contentUTF8());
            return body == null ? null : body.getString("code");
        } catch (RuntimeException parseFailure) {
            return null;
        }
    }

    private ProductMaintenanceOfferingException failure(
            ProductMaintenanceOfferingFailureReason reason, String message) {
        return new ProductMaintenanceOfferingException(reason, message);
    }

    private ProductMaintenanceOfferingException failure(
            ProductMaintenanceOfferingFailureReason reason, String message, Throwable cause) {
        return new ProductMaintenanceOfferingException(reason, message, cause);
    }
}
