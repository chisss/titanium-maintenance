package com.titanium.maintenance.infrastructure.adapter.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.time.LocalDateTime;
import java.util.function.Function;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.maintenance.common.exception.MaintenanceRemoteCallException;
import com.titanium.maintenance.common.exception.PolicyLoanRepaymentUnavailableException;
import com.titanium.maintenance.common.exception.PolicyLoanUnavailableException;
import com.titanium.maintenance.infrastructure.client.policy.PolicyLoanClient;
import com.titanium.maintenance.port.policy.PolicyLoanPort.PolicyLoanDisbursementOrder;
import com.titanium.maintenance.port.policy.PolicyLoanPort.PolicyLoanDisbursementReceipt;
import com.titanium.maintenance.port.policy.PolicyLoanPort.PolicyLoanRepaymentOrder;
import com.titanium.maintenance.port.policy.PolicyLoanPort.PolicyLoanRepaymentReceipt;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.api.request.policy.DisbursePolicyLoanRequest;
import com.titanium.policy.api.request.policy.RepayPolicyLoanRequest;
import com.titanium.policy.api.response.policy.DisbursePolicyLoanResponse;
import com.titanium.policy.api.response.policy.RepayPolicyLoanResponse;

/**
 * 保单贷款适配器测试（g04-03 放款 / g04-04 还款）。
 * <p>
 * 守失败关闭三分面（D13 范式）：① 保单域不可达（抛异常）→ {@code MaintenanceRemoteCallException}
 * （HTTP 502 语义）；② 正常应答但业务码失败/应答体无效 → 各自的 {@code XxxUnavailableException}；
 * ③ 应答回显不一致 → 契约无效同「业务失败」。三种失败均不得降级为 0/空回执兜底。
 * </p>
 */
class PolicyLoanAdapterTest {

    private static final String LOAN_ID = "LOAN001";
    private static final String POLICY_ID = "P1";
    private static final String REPAYMENT_ID = "RPAY001";

    private final PolicyLoanDisbursementOrder disbursementOrder = new PolicyLoanDisbursementOrder(
            LOAN_ID, POLICY_ID, new BigDecimal("5000"), new BigDecimal("0.0500"),
            LocalDateTime.of(2026, 9, 28, 12, 0));

    private final PolicyLoanRepaymentOrder repaymentOrder = new PolicyLoanRepaymentOrder(
            REPAYMENT_ID, POLICY_ID, LOAN_ID, new BigDecimal("911.10"),
            LocalDateTime.of(2026, 9, 28, 12, 0));

    @Test
    void shouldMapSuccessfulDisbursementIntoDomainReceipt() {
        PolicyLoanAdapter adapter = new PolicyLoanAdapter(disburseClient(
                request -> ApiResponse.success(disburseResponse(
                        LOAN_ID, POLICY_ID, new BigDecimal("5000"), new BigDecimal("0.0500"),
                        LocalDateTime.of(2026, 9, 28, 12, 0)))));

        PolicyLoanDisbursementReceipt receipt = adapter.disburse(disbursementOrder);

        assertEquals(LOAN_ID, receipt.loanId());
        assertEquals(POLICY_ID, receipt.policyId());
        assertEquals(new BigDecimal("5000"), receipt.loanAmount());
        assertEquals(new BigDecimal("0.0500"), receipt.interestRate());
        assertEquals(LocalDateTime.of(2026, 9, 28, 12, 0), receipt.loanDate());
    }

    @Test
    @DisplayName("幂等重试时回执必须原样回显（利率与放款日期以首次入账为准，编排器据此落事实）")
    void shouldEchoFirstDisbursementFactsVerbatim() {
        PolicyLoanAdapter adapter = new PolicyLoanAdapter(disburseClient(
                request -> ApiResponse.success(disburseResponse(
                        LOAN_ID, POLICY_ID, new BigDecimal("5000"), new BigDecimal("0.0480"),
                        LocalDateTime.of(2026, 9, 27, 10, 30)))));

        PolicyLoanDisbursementReceipt receipt = adapter.disburse(disbursementOrder);

        assertEquals(new BigDecimal("0.0480"), receipt.interestRate(),
                "回执利率必须原样透传（重试时与请求载荷不同是幂等语义的正常形态）");
        assertEquals(LocalDateTime.of(2026, 9, 27, 10, 30), receipt.loanDate(),
                "回执放款日期必须原样透传（首次入账日期为权威）");
    }

    @Test
    @DisplayName("保单域不可达时抛远程调用异常（HTTP 502 语义），不得伪装成放款失败")
    void shouldThrowRemoteCallExceptionWhenPolicyUnreachable() {
        PolicyLoanAdapter adapter = new PolicyLoanAdapter(disburseClient(request -> {
            throw new RuntimeException(new ConnectException("Connection refused: titanium-policy"));
        }));

        MaintenanceRemoteCallException exception = assertThrows(MaintenanceRemoteCallException.class,
                () -> adapter.disburse(disbursementOrder));
        assertEquals(MaintenanceErrorCode.MAINTENANCE_POLICY_REMOTE_ERROR.getCode(),
                exception.getErrorCodeValue());
    }

    @Test
    @DisplayName("保单域正常应答但业务码失败：放款不可得，不升级为不可达")
    void shouldThrowLoanUnavailableWhenDownstreamBusinessCodeFailed() {
        PolicyLoanAdapter adapter = new PolicyLoanAdapter(
                disburseClient(request -> ApiResponse.passthrough("20000000", "保单不存在")));

        PolicyLoanUnavailableException exception = assertThrows(PolicyLoanUnavailableException.class,
                () -> adapter.disburse(disbursementOrder));
        assertEquals(MaintenanceErrorCode.MAINTENANCE_LOAN_DISBURSEMENT_UNAVAILABLE.getCode(),
                exception.getErrorCodeValue());
    }

    @Test
    @DisplayName("应答体为 null 或缺 data：契约无效，同放款失败处置")
    void shouldThrowLoanUnavailableWhenResponsePayloadInvalid() {
        PolicyLoanAdapter nullResponseAdapter = new PolicyLoanAdapter(disburseClient(request -> null));
        assertThrows(PolicyLoanUnavailableException.class,
                () -> nullResponseAdapter.disburse(disbursementOrder));

        PolicyLoanAdapter nullDataAdapter = new PolicyLoanAdapter(
                disburseClient(request -> ApiResponse.success(null)));
        assertThrows(PolicyLoanUnavailableException.class,
                () -> nullDataAdapter.disburse(disbursementOrder));
    }

    @Test
    @DisplayName("应答贷款号或保单号与请求不符：契约无效，不做任何本地推断")
    void shouldThrowLoanUnavailableWhenEchoMismatched() {
        PolicyLoanAdapter loanMismatchAdapter = new PolicyLoanAdapter(disburseClient(
                request -> ApiResponse.success(disburseResponse(
                        "OTHER-LOAN", POLICY_ID, new BigDecimal("5000"), new BigDecimal("0.0500"),
                        LocalDateTime.of(2026, 9, 28, 12, 0)))));
        assertThrows(PolicyLoanUnavailableException.class,
                () -> loanMismatchAdapter.disburse(disbursementOrder));

        PolicyLoanAdapter policyMismatchAdapter = new PolicyLoanAdapter(disburseClient(
                request -> ApiResponse.success(disburseResponse(
                        LOAN_ID, "OTHER-POLICY", new BigDecimal("5000"), new BigDecimal("0.0500"),
                        LocalDateTime.of(2026, 9, 28, 12, 0)))));
        assertThrows(PolicyLoanUnavailableException.class,
                () -> policyMismatchAdapter.disburse(disbursementOrder));
    }

    // ==================== 还款（g04-04） ====================

    @Test
    @DisplayName("成功还款：利息/本金拆分与还款后余额原样透传（权威在对端，本域不复算）")
    void shouldMapSuccessfulRepaymentIntoDomainReceipt() {
        PolicyLoanAdapter adapter = new PolicyLoanAdapter(repayClient(
                request -> ApiResponse.success(repayResponse(
                        REPAYMENT_ID, POLICY_ID, LOAN_ID, new BigDecimal("911.10"),
                        new BigDecimal("11.10"), new BigDecimal("900.00"), new BigDecimal("11.10"),
                        BigDecimal.ZERO, BigDecimal.ZERO, true))));

        PolicyLoanRepaymentReceipt receipt = adapter.repay(repaymentOrder);

        assertEquals(REPAYMENT_ID, receipt.repaymentId());
        assertEquals(new BigDecimal("11.10"), receipt.interestPortion());
        assertEquals(new BigDecimal("900.00"), receipt.principalPortion());
        assertEquals(new BigDecimal("11.10"), receipt.accruedInterest());
        assertEquals(BigDecimal.ZERO, receipt.outstandingPrincipalAfter());
        assertEquals(BigDecimal.ZERO, receipt.outstandingInterestAfter());
        assertEquals(true, receipt.settled());
        assertEquals(LocalDateTime.of(2026, 9, 28, 12, 0), receipt.repaymentDate());
    }

    @Test
    @DisplayName("保单域不可达时抛远程调用异常（HTTP 502 语义），不得伪装成还款失败")
    void shouldThrowRemoteCallExceptionWhenPolicyUnreachableOnRepay() {
        PolicyLoanAdapter adapter = new PolicyLoanAdapter(repayClient(request -> {
            throw new RuntimeException(new ConnectException("Connection refused: titanium-policy"));
        }));

        MaintenanceRemoteCallException exception = assertThrows(MaintenanceRemoteCallException.class,
                () -> adapter.repay(repaymentOrder));
        assertEquals(MaintenanceErrorCode.MAINTENANCE_POLICY_REMOTE_ERROR.getCode(),
                exception.getErrorCodeValue());
    }

    @Test
    @DisplayName("保单域正常应答但业务码失败（贷款不存在/还款超额）：还款不可得，不升级为不可达")
    void shouldThrowRepaymentUnavailableWhenDownstreamBusinessCodeFailed() {
        PolicyLoanAdapter adapter = new PolicyLoanAdapter(
                repayClient(request -> ApiResponse.passthrough("20000000", "贷款明细不存在")));

        PolicyLoanRepaymentUnavailableException exception = assertThrows(
                PolicyLoanRepaymentUnavailableException.class, () -> adapter.repay(repaymentOrder));
        assertEquals(MaintenanceErrorCode.MAINTENANCE_LOAN_REPAYMENT_UNAVAILABLE.getCode(),
                exception.getErrorCodeValue());
    }

    @Test
    @DisplayName("应答还款号/保单号/贷款号与请求不符或应答体无效：契约无效，同还款失败处置")
    void shouldThrowRepaymentUnavailableWhenEchoMismatched() {
        PolicyLoanAdapter nullDataAdapter = new PolicyLoanAdapter(
                repayClient(request -> ApiResponse.success(null)));
        assertThrows(PolicyLoanRepaymentUnavailableException.class,
                () -> nullDataAdapter.repay(repaymentOrder));

        PolicyLoanAdapter repaymentMismatchAdapter = new PolicyLoanAdapter(repayClient(
                request -> ApiResponse.success(repayResponse(
                        "OTHER-REPAYMENT", POLICY_ID, LOAN_ID, new BigDecimal("911.10"),
                        new BigDecimal("11.10"), new BigDecimal("900.00"), new BigDecimal("11.10"),
                        BigDecimal.ZERO, BigDecimal.ZERO, true))));
        assertThrows(PolicyLoanRepaymentUnavailableException.class,
                () -> repaymentMismatchAdapter.repay(repaymentOrder));

        PolicyLoanAdapter loanMismatchAdapter = new PolicyLoanAdapter(repayClient(
                request -> ApiResponse.success(repayResponse(
                        REPAYMENT_ID, POLICY_ID, "OTHER-LOAN", new BigDecimal("911.10"),
                        new BigDecimal("11.10"), new BigDecimal("900.00"), new BigDecimal("11.10"),
                        BigDecimal.ZERO, BigDecimal.ZERO, true))));
        assertThrows(PolicyLoanRepaymentUnavailableException.class,
                () -> loanMismatchAdapter.repay(repaymentOrder));
    }

    /** 构造字段齐备的保单域放款响应。 */
    private DisbursePolicyLoanResponse disburseResponse(
            String loanId, String policyId, BigDecimal loanAmount,
            BigDecimal interestRate, LocalDateTime loanDate) {
        return new DisbursePolicyLoanResponse(loanId, policyId, loanAmount, interestRate, loanDate);
    }

    /** 构造字段齐备的保单域还款响应（分配与余额由调用方给出，模拟对端权威值）。 */
    private RepayPolicyLoanResponse repayResponse(
            String repaymentId, String policyId, String loanId, BigDecimal repaymentAmount,
            BigDecimal interestPortion, BigDecimal principalPortion, BigDecimal accruedInterest,
            BigDecimal outstandingPrincipalAfter, BigDecimal outstandingInterestAfter, boolean settled) {
        return new RepayPolicyLoanResponse(repaymentId, policyId, loanId, repaymentAmount,
                interestPortion, principalPortion, accruedInterest,
                outstandingPrincipalAfter, outstandingInterestAfter, settled,
                LocalDateTime.of(2026, 9, 28, 12, 0));
    }

    /**
     * 放款应答的测试替身。
     * <p>
     * 🔴 契约接口已有两个方法，lambda 不再可用，故以匿名实现只覆写被测方法，另一方法显式拒绝——
     * 若用例意外走到未覆盖方法即当场失败，而非静默返回 null 被当成「应答无效」。
     * </p>
     */
    private static PolicyLoanClient disburseClient(
            Function<DisbursePolicyLoanRequest, ApiResponse<DisbursePolicyLoanResponse>> responder) {
        return new PolicyLoanClient() {
            @Override
            public ApiResponse<DisbursePolicyLoanResponse> disburse(DisbursePolicyLoanRequest request) {
                return responder.apply(request);
            }

            @Override
            public ApiResponse<RepayPolicyLoanResponse> repay(RepayPolicyLoanRequest request) {
                throw new UnsupportedOperationException("本用例不涉及还款");
            }
        };
    }

    /** 还款应答的测试替身（与 {@link #disburseClient} 对称）。 */
    private static PolicyLoanClient repayClient(
            Function<RepayPolicyLoanRequest, ApiResponse<RepayPolicyLoanResponse>> responder) {
        return new PolicyLoanClient() {
            @Override
            public ApiResponse<DisbursePolicyLoanResponse> disburse(DisbursePolicyLoanRequest request) {
                throw new UnsupportedOperationException("本用例不涉及放款");
            }

            @Override
            public ApiResponse<RepayPolicyLoanResponse> repay(RepayPolicyLoanRequest request) {
                return responder.apply(request);
            }
        };
    }
}
