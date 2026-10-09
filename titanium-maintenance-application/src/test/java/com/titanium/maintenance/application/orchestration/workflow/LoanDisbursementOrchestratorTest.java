package com.titanium.maintenance.application.orchestration.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.titanium.maintenance.application.model.effect.LoanDisbursementFact;
import com.titanium.maintenance.application.model.effect.LoanDisbursementOrder;
import com.titanium.maintenance.common.exception.MaintenanceValidationException;
import com.titanium.maintenance.port.billing.PolicyLoanPostingPort;
import com.titanium.maintenance.port.billing.PolicyLoanPostingPort.PolicyLoanPostingOrder;
import com.titanium.maintenance.port.billing.PolicyLoanPostingPort.PolicyLoanPostingReceipt;
import com.titanium.maintenance.port.payment.LoanDisbursementPayoutPort;
import com.titanium.maintenance.port.payment.LoanDisbursementPayoutPort.LoanPayoutOrder;
import com.titanium.maintenance.port.payment.LoanDisbursementPayoutPort.LoanPayoutReceipt;
import com.titanium.maintenance.port.policy.PolicyFinancialPort;
import com.titanium.maintenance.port.policy.PolicyFinancialPort.PolicyFinancialData;
import com.titanium.maintenance.port.policy.PolicyLoanPort;
import com.titanium.maintenance.port.policy.PolicyLoanPort.PolicyLoanDisbursementOrder;
import com.titanium.maintenance.port.policy.PolicyLoanPort.PolicyLoanDisbursementReceipt;
import com.titanium.maintenance.port.policy.PolicyServicePort;
import com.titanium.maintenance.port.policy.PolicyServicePort.PolicyFinancialSnapshot;
import com.titanium.maintenance.port.product.ProductLoanPolicyPort;
import com.titanium.maintenance.port.product.ProductLoanPolicyPort.LoanPolicy;
import com.titanium.maintenance.service.impl.PolicyLoanDomainServiceImpl;
import com.titanium.metadata.errorcode.MaintenanceErrorCode;

/**
 * 保单贷款放款编排器测试（g04-03）。
 * <p>
 * 核心断言：① 三连写严格顺序（取数 → 核定 → 保单域放款 → 计费域入账 → 支付域出账）；
 * ② 幂等重试时要素一律取保单域回执回显（金额/利率/日期），不得沿用请求载荷；
 * ③ 额度超限拒绝必须携带 71 段错误码，且三写端口零交互（拒绝发生在任何写入之前）；
 * ④ loanId 稳定派生（32 字符、确定性、案件级唯一）。
 * </p>
 */
class LoanDisbursementOrchestratorTest {

    private static final String TENANT_ID = "T1";
    private static final String MAINTENANCE_ID = "M1";
    private static final String POLICY_ID = "P1";
    private static final String PRODUCT_ID = "PROD-1";
    private static final String CUSTOMER_ID = "CUST-1";
    private static final String OPERATOR_ID = "op-1";
    private static final LocalDateTime LOAN_DATE = LocalDateTime.of(2026, 9, 28, 12, 0);

    private PolicyFinancialPort policyFinancialPort;
    private PolicyServicePort policyServicePort;
    private ProductLoanPolicyPort productLoanPolicyPort;
    private PolicyLoanPort policyLoanPort;
    private PolicyLoanPostingPort policyLoanPostingPort;
    private LoanDisbursementPayoutPort loanDisbursementPayoutPort;
    private LoanDisbursementOrchestrator orchestrator;

    private final LoanDisbursementOrder order = new LoanDisbursementOrder(
            TENANT_ID, MAINTENANCE_ID, POLICY_ID, PRODUCT_ID, CUSTOMER_ID,
            OPERATOR_ID, new BigDecimal("5000"), LOAN_DATE);

    @BeforeEach
    void setUp() {
        policyFinancialPort = mock(PolicyFinancialPort.class);
        policyServicePort = mock(PolicyServicePort.class);
        productLoanPolicyPort = mock(ProductLoanPolicyPort.class);
        policyLoanPort = mock(PolicyLoanPort.class);
        policyLoanPostingPort = mock(PolicyLoanPostingPort.class);
        loanDisbursementPayoutPort = mock(LoanDisbursementPayoutPort.class);
        orchestrator = new LoanDisbursementOrchestrator(
                policyFinancialPort, policyServicePort, productLoanPolicyPort, policyLoanPort,
                policyLoanPostingPort, loanDisbursementPayoutPort, new PolicyLoanDomainServiceImpl());
        stubFinancialFacts(new BigDecimal("8800.00"), new BigDecimal("0.8000"), new BigDecimal("0.0500"));
        when(policyLoanPort.disburse(any(PolicyLoanDisbursementOrder.class))).thenReturn(
                new PolicyLoanDisbursementReceipt(
                        LoanDisbursementOrchestrator.stableLoanId(TENANT_ID, MAINTENANCE_ID),
                        POLICY_ID, new BigDecimal("5000"), new BigDecimal("0.0500"), LOAN_DATE));
        when(policyLoanPostingPort.postLoanDisbursement(any(PolicyLoanPostingOrder.class))).thenReturn(
                new PolicyLoanPostingReceipt("POSTING-9",
                        LoanDisbursementOrchestrator.stableLoanId(TENANT_ID, MAINTENANCE_ID)));
        when(loanDisbursementPayoutPort.payOut(any(LoanPayoutOrder.class))).thenReturn(
                new LoanPayoutReceipt("PAYOUT-7",
                        LoanDisbursementOrchestrator.stableLoanId(TENANT_ID, MAINTENANCE_ID)));
    }

    @Test
    @DisplayName("放款三连写必须严格顺序执行：取数 → 核定 → 保单域 → 计费域 → 支付域")
    void shouldDisburseLoanInStrictThreeWriteOrder() {
        LoanDisbursementFact fact = orchestrator.disburse(order);

        InOrder inOrder = inOrder(policyFinancialPort, policyServicePort, productLoanPolicyPort,
                policyLoanPort, policyLoanPostingPort, loanDisbursementPayoutPort);
        inOrder.verify(policyFinancialPort).getFinancialSnapshot(POLICY_ID, TENANT_ID);
        inOrder.verify(policyServicePort).getPolicyFinancialSnapshot(POLICY_ID, TENANT_ID);
        inOrder.verify(productLoanPolicyPort).queryEffectiveLoanPolicy(PRODUCT_ID, null);
        inOrder.verify(policyLoanPort).disburse(any(PolicyLoanDisbursementOrder.class));
        inOrder.verify(policyLoanPostingPort).postLoanDisbursement(any(PolicyLoanPostingOrder.class));
        inOrder.verify(loanDisbursementPayoutPort).payOut(any(LoanPayoutOrder.class));

        assertEquals(LoanDisbursementOrchestrator.stableLoanId(TENANT_ID, MAINTENANCE_ID), fact.loanId());
        assertEquals(POLICY_ID, fact.policyId());
        assertEquals(new BigDecimal("5000"), fact.loanAmount());
        assertEquals(new BigDecimal("0.0500"), fact.interestRate());
        assertEquals(LOAN_DATE, fact.loanDate());
        assertEquals("POSTING-9", fact.billingPostingId());
        assertEquals("PAYOUT-7", fact.paymentOrderId());
    }

    @Test
    @DisplayName("幂等重试：要素一律取保单域回执回显（金额/利率/日期），不得沿用请求载荷")
    void shouldUseReceiptEchoedFactsForPostingAndPayout() {
        // 模拟重试：保单域幂等放行并回显首次入账事实（与本次请求载荷不同）
        BigDecimal firstAmount = new BigDecimal("4800");
        BigDecimal firstRate = new BigDecimal("0.0480");
        LocalDateTime firstDate = LocalDateTime.of(2026, 9, 27, 10, 30);
        when(policyLoanPort.disburse(any(PolicyLoanDisbursementOrder.class))).thenReturn(
                new PolicyLoanDisbursementReceipt(
                        LoanDisbursementOrchestrator.stableLoanId(TENANT_ID, MAINTENANCE_ID),
                        POLICY_ID, firstAmount, firstRate, firstDate));

        LoanDisbursementFact fact = orchestrator.disburse(order);

        assertEquals(firstAmount, fact.loanAmount(), "放款金额必须取回执回显（首次入账为权威）");
        assertEquals(firstRate, fact.interestRate(), "利率必须取回执回显，不得沿用本次请求策略利率");
        assertEquals(firstDate, fact.loanDate(), "放款日期必须取回执回显，不得沿用本次执行时点");
        assertEquals("POSTING-9", fact.billingPostingId());
        assertEquals("PAYOUT-7", fact.paymentOrderId());
    }

    @Test
    @DisplayName("额度超限：拒绝携 71 段错误码，且任何写入端口零交互")
    void shouldRejectWhenQuotaExceededWith71SegmentCode() {
        stubFinancialFacts(new BigDecimal("10000"), new BigDecimal("0.8000"), new BigDecimal("0.0500"));
        LoanDisbursementOrder oversized = new LoanDisbursementOrder(
                TENANT_ID, MAINTENANCE_ID, POLICY_ID, PRODUCT_ID, CUSTOMER_ID,
                OPERATOR_ID, new BigDecimal("9000"), LOAN_DATE);

        MaintenanceValidationException exception = assertThrows(MaintenanceValidationException.class,
                () -> orchestrator.disburse(oversized));

        assertEquals(MaintenanceErrorCode.MAINTENANCE_LOAN_EXCEEDS_QUOTA.getCode(),
                exception.getErrorCodeValue(), "超限拒绝必须携带 71 段额度错误码（红线 19）");
        verifyNoInteractions(policyLoanPort, policyLoanPostingPort, loanDisbursementPayoutPort);
    }

    @Test
    @DisplayName("loanId 稳定派生：32 字符、LOAN 前缀、同输入确定性、不同案件互斥")
    void shouldDeriveStableLoanId() {
        String loanId = LoanDisbursementOrchestrator.stableLoanId(TENANT_ID, MAINTENANCE_ID);

        assertEquals(32, loanId.length(), "必须满足保单域契约 @Size(max=32)");
        assertEquals("LOAN", loanId.substring(0, 4));
        assertEquals(loanId, LoanDisbursementOrchestrator.stableLoanId(TENANT_ID, MAINTENANCE_ID),
                "同租户同案件必须幂等稳定（重试换号即同一案件产生两笔贷款）");
        assertNotEquals(loanId, LoanDisbursementOrchestrator.stableLoanId(TENANT_ID, "M2"),
                "不同案件不得撞号");
        assertNotEquals(loanId, LoanDisbursementOrchestrator.stableLoanId("T2", MAINTENANCE_ID),
                "不同租户不得撞号");
    }

    /** 桩财务快照 + 保单快照 + 贷款策略（现金价值充足、可贷比例 0.8、年利率 5%）。 */
    private void stubFinancialFacts(BigDecimal cashValue, BigDecimal loanableRatio, BigDecimal interestRate) {
        when(policyFinancialPort.getFinancialSnapshot(POLICY_ID, TENANT_ID)).thenReturn(
                new PolicyFinancialData(
                        POLICY_ID, cashValue, new BigDecimal("100000"), "PP-MAIN",
                        BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, cashValue, LocalDate.of(2025, 1, 1), 2));
        when(policyServicePort.getPolicyFinancialSnapshot(POLICY_ID, TENANT_ID)).thenReturn(
                new PolicyFinancialSnapshot(PRODUCT_ID, "ISSUE-1", LocalDate.of(2025, 1, 1),
                        new BigDecimal("100000"), "CNY"));
        when(productLoanPolicyPort.queryEffectiveLoanPolicy(PRODUCT_ID, null)).thenReturn(
                new LoanPolicy(POLICY_ID, PRODUCT_ID, loanableRatio, interestRate, "hash-abc"));
    }
}
