package com.titanium.maintenance.application.orchestration.workflow;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.stereotype.Service;

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
import com.titanium.maintenance.service.PolicyLoanDomainService;
import com.titanium.maintenance.valueobject.PolicyLoanDetail;
import com.titanium.maintenance.valueobject.PolicyLoanResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保单贷款放款编排器（application/orchestration，g04-03 生效出口）。
 * <p>
 * 贷款类保全案件生效的实质内容 = 跨保单/计费/支付三域的「放款三连写」：取数（财务快照 + 贷款策略）
 * → 领域服务核定额度 → 保单域放款 → 计费域入账 → 支付域出账。本编排器只做取数与编排，业务规则
 * （额度核定、单利计息）内聚在 {@link PolicyLoanDomainService} 与 {@link PolicyLoanDetail}，远程调用
 * 的失败关闭三分面（不可达 502 / 业务码失败 / 回显不符）由各 Adapter 承担，本编排器不兜底、不重试。
 * </p>
 * <p>
 * 🔴 <b>loanId 幂等铁律</b>：贷款明细业务号由本编排器按「租户 + 案件号」稳定派生
 * （{@code "LOAN" + sha256 前 28 位}，32 字符，满足保单域契约 {@code @Size(max=32)}）——三连写
 * 部分失败后重试，loanId 必须保持不变：保单域贷款账户聚合同键<b>同额</b>重试幂等放行并回显首次
 * 明细（利率与放款日期以首次入账为准），计费域/支付域同键幂等返回既有事实；若重试换新号，保单域
 * 会追加第二笔放款明细，同一案件即产生两笔贷款，属业务错账。金额一致性由调用方请求哈希锁定，
 * 保单域另以「同键异额拒绝」兜底（串号防御）。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoanDisbursementOrchestrator {

    private final PolicyFinancialPort policyFinancialPort;
    private final PolicyServicePort policyServicePort;
    private final ProductLoanPolicyPort productLoanPolicyPort;
    private final PolicyLoanPort policyLoanPort;
    private final PolicyLoanPostingPort policyLoanPostingPort;
    private final LoanDisbursementPayoutPort loanDisbursementPayoutPort;
    private final PolicyLoanDomainService policyLoanDomainService;

    /**
     * 执行放款三连写（同步编排，任一环节失败即抛异常失败关闭，绝不返回部分事实）。
     *
     * @param order 放款指令（金额为案件冻结的申请额；放款日期为本次执行时点，幂等重试时以保单域首次入账为准）
     * @return 放款事实（要素一律取自保单域回执回显，保证重试与首次一致）
     */
    public LoanDisbursementFact disburse(LoanDisbursementOrder order) {
        PolicyFinancialData financial = policyFinancialPort.getFinancialSnapshot(
                order.policyId(), order.tenantId());
        PolicyFinancialSnapshot snapshot = policyServicePort.getPolicyFinancialSnapshot(
                order.policyId(), order.tenantId());
        LoanPolicy loanPolicy = productLoanPolicyPort.queryEffectiveLoanPolicy(order.productId(), null);

        PolicyLoanResult decision = policyLoanDomainService.evaluateLoan(new PolicyLoanDetail(
                financial.cashValue(), loanPolicy.loanableRatio(),
                financial.loanOutstandingPrincipal(), financial.loanOutstandingInterest(),
                order.requestedAmount(), loanPolicy.interestRate()));
        if (!decision.approved()) {
            throw new MaintenanceValidationException(
                    decision.errorCode(), "LoanDisbursementOrchestrator", decision.rejectReason());
        }

        String loanId = stableLoanId(order.tenantId(), order.maintenanceId());
        PolicyLoanDisbursementReceipt disburseReceipt = policyLoanPort.disburse(
                new PolicyLoanDisbursementOrder(
                        loanId, order.policyId(), decision.loanAmount(),
                        loanPolicy.interestRate(), order.loanDate()));
        // 要素一律以保单域回执为准：幂等重试时回执回显首次入账的利率与放款日期，保证三域一致
        BigDecimal disbursedAmount = disburseReceipt.loanAmount();
        PolicyLoanPostingReceipt postingReceipt = policyLoanPostingPort.postLoanDisbursement(
                new PolicyLoanPostingOrder(
                        loanId, order.policyId(), disbursedAmount, snapshot.currency(),
                        order.operatorId(), order.tenantId()));
        LoanPayoutReceipt payoutReceipt = loanDisbursementPayoutPort.payOut(new LoanPayoutOrder(
                loanId, order.policyId(), disbursedAmount, snapshot.currency(), order.customerId()));

        return new LoanDisbursementFact(
                loanId, order.policyId(), disbursedAmount, disburseReceipt.interestRate(),
                disburseReceipt.loanDate(), postingReceipt.postingId(), payoutReceipt.paymentOrderId());
    }

    /** 稳定贷款明细业务号：租户 + 案件号派生（重试不变、案件级唯一、满足对端 32 字符上限）。 */
    static String stableLoanId(String tenantId, String maintenanceId) {
        return "LOAN" + sha256(tenantId + "\n" + maintenanceId).substring(0, 28);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK缺少SHA-256实现", exception);
        }
    }
}
