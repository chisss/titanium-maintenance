package com.titanium.maintenance.infrastructure.client.payment;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.titanium.metadata.response.ApiResponse;
import com.titanium.payment.api.request.loan.CreateLoanDisbursementRequest;
import com.titanium.payment.api.response.loan.CreateLoanDisbursementResponse;

/**
 * 保单贷款放款出账的保全域 Feign 客户端（g04-03）。
 * <p>
 * 消费支付域 {@code LoanDisbursementApi}（POST /api/v1/loan-disbursements），入参为支付域 api 层
 * {@code CreateLoanDisbursementRequest}、返回类型为 {@code CreateLoanDisbursementResponse} 强类型（禁 Map/Object）。
 * 租户与操作人经 {@code RequestContextFeignInterceptor} 请求头透传（下游经 {@code X-Tenant-Id}/
 * {@code X-Operator-Id} 解析），请求体不承载。
 * </p>
 */
@FeignClient(name = "titanium-payment-service", contextId = "loanDisbursementApi", path = "/api/v1/loan-disbursements")
public interface LoanDisbursementPayoutClient {

    @PostMapping
    ApiResponse<CreateLoanDisbursementResponse> createLoanDisbursement(
            @RequestBody CreateLoanDisbursementRequest request);
}
