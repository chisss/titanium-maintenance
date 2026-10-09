package com.titanium.maintenance.infrastructure.adapter.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.net.ConnectException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.exception.MaintenanceRemoteCallException;
import com.titanium.maintenance.common.exception.PolicyLoanPayoutUnavailableException;
import com.titanium.maintenance.infrastructure.client.payment.LoanDisbursementPayoutClient;
import com.titanium.maintenance.port.payment.LoanDisbursementPayoutPort.LoanPayoutOrder;
import com.titanium.maintenance.port.payment.LoanDisbursementPayoutPort.LoanPayoutReceipt;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.payment.api.request.loan.CreateLoanDisbursementRequest;
import com.titanium.payment.api.response.loan.CreateLoanDisbursementResponse;

/**
 * 保单贷款放款出账适配器测试（g04-03）。
 * <p>
 * 守失败关闭三分面（D13 范式）：① 支付域不可达（抛异常）→ {@code MaintenanceRemoteCallException}
 * （HTTP 502 语义）；② 正常应答但业务码失败/应答体无效 → {@code PolicyLoanPayoutUnavailableException}；
 * ③ 应答回显不一致 → 契约无效同「出账失败」。三种失败均不得降级为 0/空指令号兜底。
 * </p>
 */
class LoanDisbursementPayoutAdapterTest {

    private static final String LOAN_ID = "LOAN001";
    private static final String POLICY_ID = "P1";

    private final LoanPayoutOrder order = new LoanPayoutOrder(
            LOAN_ID, POLICY_ID, new BigDecimal("5000"), "CNY", "CUST-1");

    @Test
    void shouldMapSuccessfulPayoutIntoDomainReceipt() {
        LoanDisbursementPayoutAdapter adapter = new LoanDisbursementPayoutAdapter(
                request -> ApiResponse.success(payoutResponse(LOAN_ID, POLICY_ID, "PAYOUT-7")));

        LoanPayoutReceipt receipt = adapter.payOut(order);

        assertEquals("PAYOUT-7", receipt.paymentOrderId());
        assertEquals(LOAN_ID, receipt.loanId());
    }

    @Test
    @DisplayName("请求体必须携带全部出账要素（客户号驱动出账收款人，缺失即对端校验失败）")
    void shouldCarryAllPayoutElementsInRequestBody() {
        final CreateLoanDisbursementRequest[] captured = new CreateLoanDisbursementRequest[1];
        LoanDisbursementPayoutAdapter adapter = new LoanDisbursementPayoutAdapter(request -> {
            captured[0] = request;
            return ApiResponse.success(payoutResponse(LOAN_ID, POLICY_ID, "PAYOUT-7"));
        });

        adapter.payOut(order);

        assertEquals(LOAN_ID, captured[0].getLoanId());
        assertEquals(POLICY_ID, captured[0].getPolicyId());
        assertEquals(new BigDecimal("5000"), captured[0].getAmount());
        assertEquals("CNY", captured[0].getCurrency());
        assertEquals("CUST-1", captured[0].getCustomerId());
    }

    @Test
    @DisplayName("支付域不可达时抛远程调用异常（HTTP 502 语义），不得伪装成出账失败")
    void shouldThrowRemoteCallExceptionWhenPaymentUnreachable() {
        LoanDisbursementPayoutClient unreachableClient = request -> {
            throw new RuntimeException(new ConnectException("Connection refused: titanium-payment"));
        };
        LoanDisbursementPayoutAdapter adapter = new LoanDisbursementPayoutAdapter(unreachableClient);

        MaintenanceRemoteCallException exception = assertThrows(MaintenanceRemoteCallException.class,
                () -> adapter.payOut(order));
        assertEquals(MaintenanceErrorCode.MAINTENANCE_PAYMENT_REMOTE_ERROR.getCode(),
                exception.getErrorCodeValue());
    }

    @Test
    @DisplayName("支付域正常应答但业务码失败：出账不可得，不升级为不可达")
    void shouldThrowPayoutUnavailableWhenDownstreamBusinessCodeFailed() {
        LoanDisbursementPayoutAdapter adapter = new LoanDisbursementPayoutAdapter(
                request -> ApiResponse.passthrough("61000000", "出账要素非法"));

        PolicyLoanPayoutUnavailableException exception = assertThrows(
                PolicyLoanPayoutUnavailableException.class, () -> adapter.payOut(order));
        assertEquals(MaintenanceErrorCode.MAINTENANCE_LOAN_PAYOUT_UNAVAILABLE.getCode(),
                exception.getErrorCodeValue());
    }

    @Test
    @DisplayName("应答体为 null 或缺 data：契约无效，同出账失败处置")
    void shouldThrowPayoutUnavailableWhenResponsePayloadInvalid() {
        LoanDisbursementPayoutAdapter nullResponseAdapter = new LoanDisbursementPayoutAdapter(
                request -> null);
        assertThrows(PolicyLoanPayoutUnavailableException.class,
                () -> nullResponseAdapter.payOut(order));

        LoanDisbursementPayoutAdapter nullDataAdapter = new LoanDisbursementPayoutAdapter(
                request -> ApiResponse.success(null));
        assertThrows(PolicyLoanPayoutUnavailableException.class,
                () -> nullDataAdapter.payOut(order));
    }

    @Test
    @DisplayName("应答贷款号或保单号与请求不符：契约无效，不做任何本地推断")
    void shouldThrowPayoutUnavailableWhenEchoMismatched() {
        LoanDisbursementPayoutAdapter loanMismatchAdapter = new LoanDisbursementPayoutAdapter(
                request -> ApiResponse.success(payoutResponse("OTHER-LOAN", POLICY_ID, "PAYOUT-7")));
        assertThrows(PolicyLoanPayoutUnavailableException.class,
                () -> loanMismatchAdapter.payOut(order));

        LoanDisbursementPayoutAdapter policyMismatchAdapter = new LoanDisbursementPayoutAdapter(
                request -> ApiResponse.success(payoutResponse(LOAN_ID, "OTHER-POLICY", "PAYOUT-7")));
        assertThrows(PolicyLoanPayoutUnavailableException.class,
                () -> policyMismatchAdapter.payOut(order));
    }

    /** 构造字段齐备的支付域出账响应（@Data POJO，逐 setter 赋值）。 */
    private CreateLoanDisbursementResponse payoutResponse(
            String loanId, String policyId, String payoutId) {
        CreateLoanDisbursementResponse response = new CreateLoanDisbursementResponse();
        response.setLoanDisbursementId(payoutId);
        response.setLoanId(loanId);
        response.setPolicyId(policyId);
        response.setAmount(new BigDecimal("5000"));
        response.setCurrency("CNY");
        return response;
    }
}
