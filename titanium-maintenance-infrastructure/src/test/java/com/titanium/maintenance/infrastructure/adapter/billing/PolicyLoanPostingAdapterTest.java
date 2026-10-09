package com.titanium.maintenance.infrastructure.adapter.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.billing.api.request.loan.CreatePolicyLoanPostingRequest;
import com.titanium.billing.api.response.loan.PolicyLoanPostingResponse;
import com.titanium.maintenance.common.exception.MaintenanceRemoteCallException;
import com.titanium.maintenance.common.exception.PolicyLoanPostingUnavailableException;
import com.titanium.maintenance.infrastructure.client.billing.PolicyLoanPostingClient;
import com.titanium.maintenance.port.billing.PolicyLoanPostingPort.PolicyLoanPostingOrder;
import com.titanium.maintenance.port.billing.PolicyLoanPostingPort.PolicyLoanPostingReceipt;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.response.ApiResponse;

/**
 * 保单贷款入账适配器测试（g04-03）。
 * <p>
 * 守失败关闭三分面（D13 范式）：① 计费域不可达（抛异常）→ {@code MaintenanceRemoteCallException}
 * （HTTP 502 语义）；② 正常应答但业务码失败/应答体无效 → {@code PolicyLoanPostingUnavailableException}；
 * ③ 应答回显不一致 → 契约无效同「入账失败」。三种失败均不得降级为 0/空流水号兜底。
 * </p>
 */
class PolicyLoanPostingAdapterTest {

    private static final String LOAN_ID = "LOAN001";
    private static final String POLICY_ID = "P1";
    private static final String TENANT_ID = "T1";

    private final PolicyLoanPostingOrder order = new PolicyLoanPostingOrder(
            LOAN_ID, POLICY_ID, new BigDecimal("5000"), "CNY", "op-1", TENANT_ID);

    @Test
    void shouldMapSuccessfulPostingIntoDomainReceipt() {
        PolicyLoanPostingAdapter adapter = new PolicyLoanPostingAdapter(
                request -> ApiResponse.success(postingResponse(LOAN_ID, "POSTING-9")));

        PolicyLoanPostingReceipt receipt = adapter.postLoanDisbursement(order);

        assertEquals("POSTING-9", receipt.postingId());
        assertEquals(LOAN_ID, receipt.loanId());
    }

    @Test
    @DisplayName("请求体必须携带租户与操作人（计费契约 body 约定，缺失即对端校验失败）")
    void shouldCarryTenantAndOperatorInRequestBody() {
        final CreatePolicyLoanPostingRequest[] captured = new CreatePolicyLoanPostingRequest[1];
        PolicyLoanPostingAdapter adapter = new PolicyLoanPostingAdapter(request -> {
            captured[0] = request;
            return ApiResponse.success(postingResponse(LOAN_ID, "POSTING-9"));
        });

        adapter.postLoanDisbursement(order);

        assertEquals(LOAN_ID, captured[0].loanId());
        assertEquals(POLICY_ID, captured[0].policyId());
        assertEquals(new BigDecimal("5000"), captured[0].amount());
        assertEquals("CNY", captured[0].currency());
        assertEquals("op-1", captured[0].createdBy());
        assertEquals(TENANT_ID, captured[0].tenantId());
    }

    @Test
    @DisplayName("计费域不可达时抛远程调用异常（HTTP 502 语义），不得伪装成入账失败")
    void shouldThrowRemoteCallExceptionWhenBillingUnreachable() {
        PolicyLoanPostingClient unreachableClient = request -> {
            throw new RuntimeException(new ConnectException("Connection refused: titanium-billing"));
        };
        PolicyLoanPostingAdapter adapter = new PolicyLoanPostingAdapter(unreachableClient);

        MaintenanceRemoteCallException exception = assertThrows(MaintenanceRemoteCallException.class,
                () -> adapter.postLoanDisbursement(order));
        assertEquals(MaintenanceErrorCode.MAINTENANCE_BILLING_REMOTE_ERROR.getCode(),
                exception.getErrorCodeValue());
    }

    @Test
    @DisplayName("计费域正常应答但业务码失败：入账不可得，不升级为不可达")
    void shouldThrowPostingUnavailableWhenDownstreamBusinessCodeFailed() {
        PolicyLoanPostingAdapter adapter = new PolicyLoanPostingAdapter(
                request -> ApiResponse.passthrough("50000000", "入账要素非法"));

        PolicyLoanPostingUnavailableException exception = assertThrows(
                PolicyLoanPostingUnavailableException.class, () -> adapter.postLoanDisbursement(order));
        assertEquals(MaintenanceErrorCode.MAINTENANCE_LOAN_POSTING_UNAVAILABLE.getCode(),
                exception.getErrorCodeValue());
    }

    @Test
    @DisplayName("应答体为 null 或缺 data：契约无效，同入账失败处置")
    void shouldThrowPostingUnavailableWhenResponsePayloadInvalid() {
        PolicyLoanPostingAdapter nullResponseAdapter = new PolicyLoanPostingAdapter(request -> null);
        assertThrows(PolicyLoanPostingUnavailableException.class,
                () -> nullResponseAdapter.postLoanDisbursement(order));

        PolicyLoanPostingAdapter nullDataAdapter = new PolicyLoanPostingAdapter(
                request -> ApiResponse.success(null));
        assertThrows(PolicyLoanPostingUnavailableException.class,
                () -> nullDataAdapter.postLoanDisbursement(order));
    }

    @Test
    @DisplayName("应答贷款号与请求不符：契约无效，不做任何本地推断")
    void shouldThrowPostingUnavailableWhenEchoMismatched() {
        PolicyLoanPostingAdapter adapter = new PolicyLoanPostingAdapter(
                request -> ApiResponse.success(postingResponse("OTHER-LOAN", "POSTING-9")));

        PolicyLoanPostingUnavailableException exception = assertThrows(
                PolicyLoanPostingUnavailableException.class, () -> adapter.postLoanDisbursement(order));
        assertEquals(MaintenanceErrorCode.MAINTENANCE_LOAN_POSTING_UNAVAILABLE.getCode(),
                exception.getErrorCodeValue());
    }

    /** 构造字段齐备的计费域入账响应（满足契约 record 全参构造）。 */
    private PolicyLoanPostingResponse postingResponse(String loanId, String postingId) {
        return new PolicyLoanPostingResponse(
                postingId, loanId, POLICY_ID, new BigDecimal("5000"), "CNY",
                "POSTED", "op-1", LocalDateTime.of(2026, 9, 28, 12, 0));
    }
}
