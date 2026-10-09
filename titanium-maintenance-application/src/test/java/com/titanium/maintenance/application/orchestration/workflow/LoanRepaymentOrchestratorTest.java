package com.titanium.maintenance.application.orchestration.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import com.titanium.maintenance.application.model.effect.LoanRepaymentFact;
import com.titanium.maintenance.application.model.effect.LoanRepaymentOrder;
import com.titanium.maintenance.common.exception.PolicyLoanRepaymentUnavailableException;
import com.titanium.maintenance.port.billing.PolicyLoanRepaymentPostingPort;
import com.titanium.maintenance.port.billing.PolicyLoanRepaymentPostingPort.PolicyLoanRepaymentPostingOrder;
import com.titanium.maintenance.port.billing.PolicyLoanRepaymentPostingPort.PolicyLoanRepaymentPostingReceipt;
import com.titanium.maintenance.port.payment.LoanRepaymentCollectionPort;
import com.titanium.maintenance.port.payment.LoanRepaymentCollectionPort.RepaymentCollectionOrder;
import com.titanium.maintenance.port.payment.LoanRepaymentCollectionPort.RepaymentCollectionReceipt;
import com.titanium.maintenance.port.policy.PolicyLoanPort;
import com.titanium.maintenance.port.policy.PolicyLoanPort.PolicyLoanRepaymentOrder;
import com.titanium.maintenance.port.policy.PolicyLoanPort.PolicyLoanRepaymentReceipt;
import com.titanium.maintenance.port.policy.PolicyServicePort;
import com.titanium.maintenance.port.policy.PolicyServicePort.PolicyFinancialSnapshot;

/**
 * 保单贷款还款编排器测试（g04-04）。
 * <p>
 * 核心断言：① 三连写严格顺序（取币种快照 → 保单域记账 → 计费域冲销 → 支付域收款）；
 * ② 幂等重试时冲销与收款的金额/拆分/日期一律取<b>保单域回执回显</b>，不得沿用请求载荷（否则同一笔钱
 * 在策略变更或重试后于三域记出不同数字）；③ repaymentId 稳定派生（32 字符、RPAY 前缀、确定性、案件级唯一）；
 * ④ 任一环节失败即整体抛出（失败关闭），不得继续写下游。
 * </p>
 */
class LoanRepaymentOrchestratorTest {

    private static final String TENANT_ID = "T1";
    private static final String MAINTENANCE_ID = "M1";
    private static final String POLICY_ID = "P1";
    private static final String PRODUCT_ID = "PROD-1";
    private static final String CUSTOMER_ID = "CUST-1";
    private static final String OPERATOR_ID = "op-1";
    private static final String LOAN_ID = "LOAN001";
    private static final LocalDateTime REPAYMENT_DATE = LocalDateTime.of(2026, 9, 28, 12, 0);

    private PolicyServicePort policyServicePort;
    private PolicyLoanPort policyLoanPort;
    private PolicyLoanRepaymentPostingPort policyLoanRepaymentPostingPort;
    private LoanRepaymentCollectionPort loanRepaymentCollectionPort;
    private LoanRepaymentOrchestrator orchestrator;

    private final LoanRepaymentOrder order = new LoanRepaymentOrder(
            TENANT_ID, MAINTENANCE_ID, POLICY_ID, CUSTOMER_ID, OPERATOR_ID,
            LOAN_ID, new BigDecimal("911.10"), REPAYMENT_DATE);

    @BeforeEach
    void setUp() {
        policyServicePort = mock(PolicyServicePort.class);
        policyLoanPort = mock(PolicyLoanPort.class);
        policyLoanRepaymentPostingPort = mock(PolicyLoanRepaymentPostingPort.class);
        loanRepaymentCollectionPort = mock(LoanRepaymentCollectionPort.class);
        orchestrator = new LoanRepaymentOrchestrator(
                policyServicePort, policyLoanPort, policyLoanRepaymentPostingPort, loanRepaymentCollectionPort);

        String repaymentId = LoanRepaymentOrchestrator.stableRepaymentId(TENANT_ID, MAINTENANCE_ID);
        when(policyServicePort.getPolicyFinancialSnapshot(POLICY_ID, TENANT_ID)).thenReturn(
                new PolicyFinancialSnapshot(PRODUCT_ID, "ISSUE-1", LocalDate.of(2025, 1, 1),
                        new BigDecimal("100000"), "CNY"));
        when(policyLoanPort.repay(any(PolicyLoanRepaymentOrder.class))).thenReturn(settledReceipt(repaymentId));
        when(policyLoanRepaymentPostingPort.postLoanRepayment(any(PolicyLoanRepaymentPostingOrder.class)))
                .thenReturn(new PolicyLoanRepaymentPostingReceipt("POSTING-9", repaymentId));
        when(loanRepaymentCollectionPort.collect(any(RepaymentCollectionOrder.class)))
                .thenReturn(new RepaymentCollectionReceipt(
                        repaymentId, POLICY_ID, "SUCCESS", "TXN-1", REPAYMENT_DATE));
    }

    @Test
    @DisplayName("还款三连写必须严格顺序执行：取币种快照 → 保单域 → 计费域 → 支付域")
    void shouldRepayLoanInStrictThreeWriteOrder() {
        LoanRepaymentFact fact = orchestrator.repay(order);

        InOrder inOrder = inOrder(policyServicePort, policyLoanPort,
                policyLoanRepaymentPostingPort, loanRepaymentCollectionPort);
        inOrder.verify(policyServicePort).getPolicyFinancialSnapshot(POLICY_ID, TENANT_ID);
        inOrder.verify(policyLoanPort).repay(any(PolicyLoanRepaymentOrder.class));
        inOrder.verify(policyLoanRepaymentPostingPort).postLoanRepayment(any(PolicyLoanRepaymentPostingOrder.class));
        inOrder.verify(loanRepaymentCollectionPort).collect(any(RepaymentCollectionOrder.class));

        assertEquals(LoanRepaymentOrchestrator.stableRepaymentId(TENANT_ID, MAINTENANCE_ID), fact.repaymentId());
        assertEquals(LOAN_ID, fact.loanId());
        assertEquals(new BigDecimal("911.10"), fact.repaymentAmount());
        assertEquals(new BigDecimal("11.10"), fact.interestPortion());
        assertEquals(new BigDecimal("900.00"), fact.principalPortion());
        assertEquals(BigDecimal.ZERO, fact.outstandingPrincipalAfter());
        assertEquals(BigDecimal.ZERO, fact.outstandingInterestAfter());
        assertEquals(true, fact.settled());
        assertEquals("POSTING-9", fact.billingPostingId());
        assertEquals(LoanRepaymentOrchestrator.stableRepaymentId(TENANT_ID, MAINTENANCE_ID), fact.paymentOrderId(),
                "支付域收款单号取回执回显（即还款业务号，本域不另造号）；渠道流水号 TXN-1 不属收款单号");
    }

    @Test
    @DisplayName("幂等重试：冲销与收款的金额/拆分/日期一律取保单域回执回显，不得沿用请求载荷")
    void shouldUseReceiptEchoedFactsForPostingAndCollection() {
        // 模拟重试：保单域幂等放行并回显首次记账事实（与本次请求载荷不同）
        String repaymentId = LoanRepaymentOrchestrator.stableRepaymentId(TENANT_ID, MAINTENANCE_ID);
        BigDecimal firstAmount = new BigDecimal("500.00");
        LocalDateTime firstDate = LocalDateTime.of(2026, 9, 27, 10, 30);
        when(policyLoanPort.repay(any(PolicyLoanRepaymentOrder.class))).thenReturn(
                new PolicyLoanRepaymentReceipt(repaymentId, POLICY_ID, LOAN_ID, firstAmount,
                        new BigDecimal("20.00"), new BigDecimal("480.00"), new BigDecimal("20.00"),
                        new BigDecimal("4520.00"), BigDecimal.ZERO, false, firstDate));

        LoanRepaymentFact fact = orchestrator.repay(order);

        assertEquals(firstAmount, fact.repaymentAmount(), "还款总额必须取回执回显（首次记账为权威）");
        assertEquals(new BigDecimal("20.00"), fact.interestPortion());
        assertEquals(new BigDecimal("480.00"), fact.principalPortion());
        assertEquals(firstDate, fact.repaymentDate(), "还款日期必须取回执回显，不得沿用本次执行时点");
        assertEquals(false, fact.settled());

        ArgumentCaptor<PolicyLoanRepaymentPostingOrder> posting =
                ArgumentCaptor.forClass(PolicyLoanRepaymentPostingOrder.class);
        ArgumentCaptor<RepaymentCollectionOrder> collection =
                ArgumentCaptor.forClass(RepaymentCollectionOrder.class);
        verify(policyLoanRepaymentPostingPort).postLoanRepayment(posting.capture());
        verify(loanRepaymentCollectionPort).collect(collection.capture());
        assertEquals(firstAmount, posting.getValue().amount(), "计费域冲销金额必须等于回执金额");
        assertEquals(new BigDecimal("20.00"), posting.getValue().interestPortion());
        assertEquals(new BigDecimal("480.00"), posting.getValue().principalPortion());
        assertEquals(firstAmount, collection.getValue().amount(), "支付域收款金额必须等于回执金额");
        assertEquals("CNY", posting.getValue().currency(), "币种取保单快照");
        assertEquals("CNY", collection.getValue().currency());
    }

    @Test
    @DisplayName("repaymentId 稳定派生：32 字符、RPAY 前缀、同输入确定性、不同案件与租户互斥")
    void shouldDeriveStableRepaymentId() {
        String repaymentId = LoanRepaymentOrchestrator.stableRepaymentId(TENANT_ID, MAINTENANCE_ID);

        assertEquals(32, repaymentId.length(), "必须满足保单域契约 @Size(max=32)");
        assertEquals("RPAY", repaymentId.substring(0, 4));
        assertEquals(repaymentId, LoanRepaymentOrchestrator.stableRepaymentId(TENANT_ID, MAINTENANCE_ID),
                "同租户同案件必须幂等稳定（重试换号即同一笔还款被记两遍）");
        assertNotEquals(repaymentId, LoanRepaymentOrchestrator.stableRepaymentId(TENANT_ID, "M2"),
                "不同案件不得撞号");
        assertNotEquals(repaymentId, LoanRepaymentOrchestrator.stableRepaymentId("T2", MAINTENANCE_ID),
                "不同租户不得撞号");
        assertNotEquals(repaymentId,
                LoanDisbursementOrchestrator.stableLoanId(TENANT_ID, MAINTENANCE_ID),
                "还款号与放款号前缀不同、不得互相冒充");
    }

    @Test
    @DisplayName("保单域还款失败即整体抛出（失败关闭），计费域与支付域零交互")
    void shouldFailClosedWhenPolicyRepayFails() {
        when(policyLoanPort.repay(any(PolicyLoanRepaymentOrder.class)))
                .thenThrow(new PolicyLoanRepaymentUnavailableException("还款额超过欠款总额"));

        assertThrows(PolicyLoanRepaymentUnavailableException.class, () -> orchestrator.repay(order));

        verifyNoInteractions(policyLoanRepaymentPostingPort, loanRepaymentCollectionPort);
    }

    /** 保单域还款回执：900.00 本金 @ 5% 计 90 天 → 11.10 利息，先息后本、结清后余额归零。 */
    private PolicyLoanRepaymentReceipt settledReceipt(String repaymentId) {
        return new PolicyLoanRepaymentReceipt(repaymentId, POLICY_ID, LOAN_ID, new BigDecimal("911.10"),
                new BigDecimal("11.10"), new BigDecimal("900.00"), new BigDecimal("11.10"),
                BigDecimal.ZERO, BigDecimal.ZERO, true, REPAYMENT_DATE);
    }
}
